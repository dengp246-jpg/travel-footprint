package com.example.travelfootprint.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.example.travelfootprint.model.StoredImage;
import com.example.travelfootprint.repository.StoredImageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    private static final long DEFAULT_MAX_IMAGE_SIZE = 5L * 1024L * 1024L;
    private static final long DEFAULT_MAX_VIDEO_SIZE = 20L * 1024L * 1024L;
    private static final Map<String, String> CONTENT_TYPE_EXTENSIONS = allowedContentTypes();
    private static final Map<String, String> VIDEO_CONTENT_TYPE_EXTENSIONS = allowedVideoContentTypes();

    private final Path uploadRoot;
    private final long maxImageSize;
    private final long maxVideoSize;
    private final StoredImageRepository storedImageRepository;
    private final boolean databaseStorage;

    @Autowired
    public FileStorageService(
            @Value("${app.upload-dir:uploads}") String uploadDir,
            @Value("${app.upload.max-image-size-bytes:5242880}") long maxImageSize,
            @Value("${app.upload.max-video-size-bytes:20971520}") long maxVideoSize,
            @Value("${app.upload.storage-mode:filesystem}") String storageMode,
            StoredImageRepository storedImageRepository) throws IOException {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.maxImageSize = maxImageSize > 0 ? maxImageSize : DEFAULT_MAX_IMAGE_SIZE;
        this.maxVideoSize = maxVideoSize > 0 ? maxVideoSize : DEFAULT_MAX_VIDEO_SIZE;
        this.storedImageRepository = storedImageRepository;
        this.databaseStorage = "database".equalsIgnoreCase(storageMode);
        if (!databaseStorage) {
            Files.createDirectories(uploadRoot);
        }
    }

    public FileStorageService(String uploadDir, long maxImageSize) throws IOException {
        this(uploadDir, maxImageSize, DEFAULT_MAX_VIDEO_SIZE, "filesystem", null);
    }

    public FileStorageService(
            String uploadDir,
            long maxImageSize,
            String storageMode,
            StoredImageRepository storedImageRepository) throws IOException {
        this(uploadDir, maxImageSize, DEFAULT_MAX_VIDEO_SIZE, storageMode, storedImageRepository);
    }

    public String store(MultipartFile file) throws IOException {
        return store(file, "");
    }

    @Transactional
    public String store(MultipartFile file, String subDirectory) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        if (file.getSize() > maxImageSize) {
            throw new IOException("图片不能超过 " + readableSize(maxImageSize) + "。");
        }

        byte[] content = file.getBytes();
        String detectedContentType = detectImageType(content);
        String declaredContentType = normalizeContentType(file.getContentType());
        if (detectedContentType == null || !CONTENT_TYPE_EXTENSIONS.containsKey(declaredContentType)
                || !detectedContentType.equals(declaredContentType)) {
            throw new IOException("仅支持 JPG、PNG、GIF 或 WebP 图片。");
        }

        return storeContent(content, detectedContentType, CONTENT_TYPE_EXTENSIONS.get(detectedContentType), subDirectory);
    }

    @Transactional
    public String storeVideo(MultipartFile file, String subDirectory) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > maxVideoSize) {
            throw new IOException("视频不能超过 " + readableSize(maxVideoSize) + "。");
        }
        byte[] content = file.getBytes();
        String detectedContentType = detectVideoType(content);
        String declaredContentType = normalizeContentType(file.getContentType());
        if (detectedContentType == null || !VIDEO_CONTENT_TYPE_EXTENSIONS.containsKey(declaredContentType)
                || !detectedContentType.equals(declaredContentType)) {
            throw new IOException("仅支持 MP4 或 WebM 视频，且文件内容必须与格式一致。");
        }
        return storeContent(
                content,
                detectedContentType,
                VIDEO_CONTENT_TYPE_EXTENSIONS.get(detectedContentType),
                subDirectory);
    }

    private String storeContent(byte[] content, String contentType, String extension, String subDirectory)
            throws IOException {
        String safeSubDirectory = normalizeSubDirectory(subDirectory);
        String newFilename = UUID.randomUUID() + extension;
        String publicPath = safeSubDirectory.isBlank()
                ? "/uploads/" + newFilename
                : "/uploads/" + safeSubDirectory + "/" + newFilename;
        if (databaseStorage) {
            StoredImage image = new StoredImage();
            image.setPublicPath(publicPath);
            image.setContentType(contentType);
            image.setContent(content);
            storedImageRepository.save(image);
            return publicPath;
        }
        Path targetDir = safeSubDirectory.isBlank()
                ? uploadRoot
                : uploadRoot.resolve(safeSubDirectory).normalize();
        ensureInsideUploadRoot(targetDir);
        Files.createDirectories(targetDir);
        Path destination = targetDir.resolve(newFilename).normalize();
        ensureInsideUploadRoot(destination);

        Files.write(destination, content, StandardOpenOption.CREATE_NEW);
        return publicPath;
    }

    @Transactional
    public void delete(String publicPath) throws IOException {
        if (publicPath == null || !publicPath.startsWith("/uploads/")
                || publicPath.contains("%") || publicPath.contains("\\")) {
            return;
        }
        if (databaseStorage) {
            storedImageRepository.deleteByPublicPath(publicPath);
            return;
        }
        Path candidate = uploadRoot.resolve(publicPath.substring("/uploads/".length())).normalize();
        ensureInsideUploadRoot(candidate);
        Files.deleteIfExists(candidate);
    }

    @Transactional(readOnly = true)
    public StoredFile load(String publicPath) throws IOException {
        if (publicPath == null || !publicPath.startsWith("/uploads/")
                || publicPath.contains("%") || publicPath.contains("\\")) {
            return null;
        }
        if (databaseStorage) {
            return storedImageRepository.findByPublicPath(publicPath)
                    .map(image -> new StoredFile(image.getContent(), image.getContentType()))
                    .orElse(null);
        }
        String relativePath = publicPath.substring("/uploads/".length());
        if (relativePath.isBlank()) {
            return null;
        }
        Path candidate = uploadRoot.resolve(relativePath).normalize();
        if (!candidate.startsWith(uploadRoot) || !Files.isRegularFile(candidate)) {
            return null;
        }
        Path realRoot = uploadRoot.toRealPath();
        Path realFile = candidate.toRealPath();
        if (!realFile.startsWith(realRoot)) {
            return null;
        }
        String type = Files.probeContentType(realFile);
        return new StoredFile(Files.readAllBytes(realFile),
                type == null ? "application/octet-stream" : type);
    }

    public boolean isReady() {
        return databaseStorage || (Files.isDirectory(uploadRoot) && Files.isWritable(uploadRoot));
    }

    public long maxVideoSizeBytes() {
        return maxVideoSize;
    }

    private String normalizeSubDirectory(String subDirectory) throws IOException {
        if (subDirectory == null || subDirectory.isBlank()) {
            return "";
        }
        String normalized = subDirectory.trim();
        if (!normalized.matches("[A-Za-z0-9_-]+")) {
            throw new IOException("上传目录不合法。");
        }
        return normalized;
    }

    private void ensureInsideUploadRoot(Path path) throws IOException {
        if (!path.startsWith(uploadRoot)) {
            throw new IOException("上传路径不合法。");
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        return contentType.trim().toLowerCase();
    }

    private String detectImageType(byte[] content) {
        if (content.length >= 8
                && (content[0] & 0xff) == 0x89
                && content[1] == 0x50
                && content[2] == 0x4e
                && content[3] == 0x47
                && content[4] == 0x0d
                && content[5] == 0x0a
                && content[6] == 0x1a
                && content[7] == 0x0a) {
            return "image/png";
        }
        if (content.length >= 3
                && (content[0] & 0xff) == 0xff
                && (content[1] & 0xff) == 0xd8
                && (content[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (content.length >= 6
                && content[0] == 'G'
                && content[1] == 'I'
                && content[2] == 'F'
                && content[3] == '8'
                && (content[4] == '7' || content[4] == '9')
                && content[5] == 'a') {
            return "image/gif";
        }
        if (content.length >= 12
                && content[0] == 'R'
                && content[1] == 'I'
                && content[2] == 'F'
                && content[3] == 'F'
                && content[8] == 'W'
                && content[9] == 'E'
                && content[10] == 'B'
                && content[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private String detectVideoType(byte[] content) {
        if (content.length >= 12
                && content[4] == 'f'
                && content[5] == 't'
                && content[6] == 'y'
                && content[7] == 'p') {
            return "video/mp4";
        }
        if (content.length >= 4
                && (content[0] & 0xff) == 0x1a
                && (content[1] & 0xff) == 0x45
                && (content[2] & 0xff) == 0xdf
                && (content[3] & 0xff) == 0xa3) {
            return "video/webm";
        }
        return null;
    }

    private static Map<String, String> allowedContentTypes() {
        Map<String, String> contentTypes = new LinkedHashMap<>();
        contentTypes.put("image/jpeg", ".jpg");
        contentTypes.put("image/png", ".png");
        contentTypes.put("image/gif", ".gif");
        contentTypes.put("image/webp", ".webp");
        return Map.copyOf(contentTypes);
    }

    private static Map<String, String> allowedVideoContentTypes() {
        Map<String, String> contentTypes = new LinkedHashMap<>();
        contentTypes.put("video/mp4", ".mp4");
        contentTypes.put("video/webm", ".webm");
        return Map.copyOf(contentTypes);
    }

    private String readableSize(long bytes) {
        if (bytes >= 1024L * 1024L && bytes % (1024L * 1024L) == 0) {
            return (bytes / (1024L * 1024L)) + "MB";
        }
        return Math.max(1L, bytes / 1024L) + "KB";
    }

    public record StoredFile(byte[] content, String contentType) {
    }
}

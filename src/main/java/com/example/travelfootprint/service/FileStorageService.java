package com.example.travelfootprint.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    private final Path uploadRoot;

    public FileStorageService(@Value("${app.upload-dir:uploads}") String uploadDir) throws IOException {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadRoot);
    }

    public String store(MultipartFile file) throws IOException {
        return store(file, "");
    }

    public String store(MultipartFile file, String subDirectory) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String originalFilename = StringUtils.cleanPath(
                Objects.requireNonNullElse(file.getOriginalFilename(), "photo.jpg"));
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex >= 0) {
            extension = originalFilename.substring(dotIndex);
        }

        String newFilename = UUID.randomUUID() + extension;
        Path targetDir = subDirectory == null || subDirectory.isBlank()
                ? uploadRoot
                : uploadRoot.resolve(subDirectory).normalize();
        Files.createDirectories(targetDir);
        Path destination = targetDir.resolve(newFilename).normalize();

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        return subDirectory == null || subDirectory.isBlank()
                ? "/uploads/" + newFilename
                : "/uploads/" + subDirectory + "/" + newFilename;
    }
}

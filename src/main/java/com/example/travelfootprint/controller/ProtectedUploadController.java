package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.TravelPostPhotoRepository;
import com.example.travelfootprint.repository.TravelPostRepository;
import com.example.travelfootprint.repository.UserRepository;
import com.example.travelfootprint.service.ContentVisibilityService;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.FileStorageService;
import com.example.travelfootprint.service.MiniAppTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.transaction.annotation.Transactional;

@Controller
public class ProtectedUploadController {

    private final FileStorageService fileStorageService;
    private final TravelPostPhotoRepository photoRepository;
    private final TravelPostRepository postRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final ContentVisibilityService visibilityService;
    private final MiniAppTokenService miniAppTokenService;

    public ProtectedUploadController(
            FileStorageService fileStorageService,
            TravelPostPhotoRepository photoRepository,
            TravelPostRepository postRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService,
            ContentVisibilityService visibilityService,
            MiniAppTokenService miniAppTokenService) {
        this.fileStorageService = fileStorageService;
        this.photoRepository = photoRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.visibilityService = visibilityService;
        this.miniAppTokenService = miniAppTokenService;
    }

    @GetMapping("/uploads/**")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> upload(
            HttpServletRequest request,
            HttpSession session,
            @RequestHeader(value = "X-Mini-Token", required = false) String tokenHeader,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            @RequestParam(value = "miniToken", required = false) String tokenQuery) throws IOException {
        String requestPath = request.getRequestURI();
        String relativePath = requestPath.length() <= "/uploads/".length()
                ? "" : requestPath.substring("/uploads/".length());
        if (relativePath.isBlank() || relativePath.contains("%") || relativePath.contains("\\")) {
            return ResponseEntity.notFound().build();
        }
        String publicPath = "/uploads/" + relativePath.replace('\\', '/');
        boolean avatar = userRepository.existsByAvatarPath(publicPath);
        User sessionViewer = currentUserService.getCurrentUser(session);
        String miniToken = tokenHeader == null || tokenHeader.isBlank() ? tokenQuery : tokenHeader;
        User viewer = sessionViewer != null ? sessionViewer : miniAppTokenService.findUser(miniToken).orElse(null);
        Optional<TravelPost> post = photoRepository.findFirstByPhotoPath(publicPath).map(item -> item.getPost())
                .or(() -> postRepository.findFirstByPhotoPath(publicPath))
                .or(() -> postRepository.findFirstByVideoPath(publicPath));
        boolean canView = avatar || post.filter(item -> visibilityService.canViewPost(viewer, item)).isPresent();
        if (!canView) {
            return ResponseEntity.notFound().build();
        }

        FileStorageService.StoredFileMetadata metadata = fileStorageService.metadata(publicPath);
        if (metadata == null || metadata.size() <= 0) {
            return ResponseEntity.notFound().build();
        }
        long start = 0;
        long end = metadata.size() - 1;
        boolean partial = rangeHeader != null && !rangeHeader.isBlank();
        if (partial) {
            try {
                var ranges = HttpRange.parseRanges(rangeHeader);
                if (ranges.size() != 1) {
                    return rangeNotSatisfiable(metadata.size());
                }
                start = ranges.get(0).getRangeStart(metadata.size());
                end = ranges.get(0).getRangeEnd(metadata.size());
            } catch (IllegalArgumentException exception) {
                return rangeNotSatisfiable(metadata.size());
            }
        }
        FileStorageService.StoredFile storedFile = fileStorageService.loadRange(publicPath, start, end);
        if (storedFile == null) {
            return rangeNotSatisfiable(metadata.size());
        }

        MediaType mediaType = MediaType.parseMediaType(metadata.contentType());
        CacheControl cache = avatar || post.filter(visibilityService::isPublicPost).isPresent()
                ? CacheControl.maxAge(Duration.ofHours(1)).cachePublic()
                : CacheControl.noStore();
        ResponseEntity.BodyBuilder response = ResponseEntity.status(partial ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .cacheControl(cache)
                .contentType(mediaType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentLength(storedFile.content().length);
        if (partial) {
            response.header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + metadata.size());
        }
        return response.body(storedFile.content());
    }

    private ResponseEntity<byte[]> rangeNotSatisfiable(long totalSize) {
        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes */" + totalSize)
                .cacheControl(CacheControl.noStore())
                .build();
    }
}

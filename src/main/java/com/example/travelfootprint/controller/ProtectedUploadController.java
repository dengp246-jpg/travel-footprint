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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
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
    public ResponseEntity<Resource> upload(
            HttpServletRequest request,
            HttpSession session,
            @RequestHeader(value = "X-Mini-Token", required = false) String tokenHeader,
            @RequestParam(value = "miniToken", required = false) String tokenQuery) throws IOException {
        String requestPath = request.getRequestURI();
        String relativePath = requestPath.length() <= "/uploads/".length()
                ? "" : requestPath.substring("/uploads/".length());
        if (relativePath.isBlank() || relativePath.contains("%") || relativePath.contains("\\")) {
            return ResponseEntity.notFound().build();
        }
        String publicPath = "/uploads/" + relativePath.replace('\\', '/');
        FileStorageService.StoredFile storedFile = fileStorageService.load(publicPath);
        if (storedFile == null) {
            return ResponseEntity.notFound().build();
        }

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

        MediaType mediaType = MediaType.parseMediaType(storedFile.contentType());
        CacheControl cache = avatar || post.filter(visibilityService::isPublicPost).isPresent()
                ? CacheControl.maxAge(Duration.ofHours(1)).cachePublic()
                : CacheControl.noStore();
        return ResponseEntity.ok()
                .cacheControl(cache)
                .contentType(mediaType)
                .contentLength(storedFile.content().length)
                .body(new ByteArrayResource(storedFile.content()));
    }
}

package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.NotificationRepository;
import com.example.travelfootprint.repository.PrivateMessageRepository;
import com.example.travelfootprint.service.AppCatalogService;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.ProvinceCatalogService;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAttributes {

    private final CurrentUserService currentUserService;
    private final AppCatalogService appCatalogService;
    private final ProvinceCatalogService provinceCatalogService;
    private final NotificationRepository notificationRepository;
    private final PrivateMessageRepository messageRepository;
    private final long maxVideoUploadBytes;

    public GlobalModelAttributes(
            CurrentUserService currentUserService,
            AppCatalogService appCatalogService,
            ProvinceCatalogService provinceCatalogService,
            NotificationRepository notificationRepository,
            PrivateMessageRepository messageRepository,
            @Value("${app.upload.max-video-size-bytes:20971520}") long maxVideoUploadBytes) {
        this.currentUserService = currentUserService;
        this.appCatalogService = appCatalogService;
        this.provinceCatalogService = provinceCatalogService;
        this.notificationRepository = notificationRepository;
        this.messageRepository = messageRepository;
        this.maxVideoUploadBytes = maxVideoUploadBytes > 0 ? maxVideoUploadBytes : 20L * 1024L * 1024L;
    }

    @ModelAttribute("currentUser")
    public User currentUser(HttpSession session) {
        return currentUserService.getCurrentUser(session);
    }

    @ModelAttribute("categories")
    public List<String> categories() {
        return appCatalogService.categories();
    }

    @ModelAttribute("provinces")
    public List<String> provinces() {
        return provinceCatalogService.provinceNames();
    }

    @ModelAttribute("maxVideoUploadBytes")
    public long maxVideoUploadBytes() {
        return maxVideoUploadBytes;
    }

    @ModelAttribute("maxVideoUploadLabel")
    public String maxVideoUploadLabel() {
        long megabyte = 1024L * 1024L;
        return maxVideoUploadBytes % megabyte == 0
                ? (maxVideoUploadBytes / megabyte) + "MB"
                : Math.max(1L, maxVideoUploadBytes / 1024L) + "KB";
    }

    @ModelAttribute("unreadNotificationCount")
    public long unreadNotificationCount(HttpSession session) {
        User currentUser = currentUserService.getCurrentUser(session);
        return currentUser == null ? 0 : notificationRepository.countByRecipientIdAndReadAtIsNull(currentUser.getId());
    }

    @ModelAttribute("unreadMessageCount")
    public long unreadMessageCount(HttpSession session) {
        User currentUser = currentUserService.getCurrentUser(session);
        return currentUser == null ? 0 : messageRepository.countByReceiverIdAndReadAtIsNull(currentUser.getId());
    }
}

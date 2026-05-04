package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.NotificationRepository;
import com.example.travelfootprint.repository.PrivateMessageRepository;
import com.example.travelfootprint.service.AppCatalogService;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.ProvinceCatalogService;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAttributes {

    private final CurrentUserService currentUserService;
    private final AppCatalogService appCatalogService;
    private final ProvinceCatalogService provinceCatalogService;
    private final NotificationRepository notificationRepository;
    private final PrivateMessageRepository messageRepository;

    public GlobalModelAttributes(
            CurrentUserService currentUserService,
            AppCatalogService appCatalogService,
            ProvinceCatalogService provinceCatalogService,
            NotificationRepository notificationRepository,
            PrivateMessageRepository messageRepository) {
        this.currentUserService = currentUserService;
        this.appCatalogService = appCatalogService;
        this.provinceCatalogService = provinceCatalogService;
        this.notificationRepository = notificationRepository;
        this.messageRepository = messageRepository;
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

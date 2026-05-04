package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.Notification;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.NotificationRepository;
import com.example.travelfootprint.service.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;

    public NotificationController(NotificationRepository notificationRepository, CurrentUserService currentUserService) {
        this.notificationRepository = notificationRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/notifications")
    public String notifications(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先登录，再查看通知中心。");
            return "redirect:/login";
        }

        List<Notification> notifications = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(currentUser.getId());
        List<Notification> unreadNotifications = notifications.stream()
                .filter(notification -> notification.getReadAt() == null)
                .peek(notification -> notification.setReadAt(LocalDateTime.now()))
                .toList();
        if (!unreadNotifications.isEmpty()) {
            notificationRepository.saveAll(unreadNotifications);
        }

        model.addAttribute("notifications", notifications);
        return "notifications";
    }
}

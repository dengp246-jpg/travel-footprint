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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
        model.addAttribute("notifications", notifications);
        model.addAttribute("unreadCount", notifications.stream()
                .filter(notification -> notification.getReadAt() == null).count());
        return "notifications";
    }

    @GetMapping("/notifications/{id}/open")
    public String openNotification(
            @PathVariable Long id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            return "redirect:/login";
        }
        Notification notification = notificationRepository.findById(id).orElseThrow();
        if (!notification.getRecipient().getId().equals(currentUser.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "你无权查看该通知。");
            return "redirect:/notifications";
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }
        String link = notification.getLinkPath();
        return link != null && link.startsWith("/") && !link.startsWith("//")
                ? "redirect:" + link : "redirect:/notifications";
    }

    @PostMapping("/notifications/read-all")
    public String markAllRead(HttpSession session) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            return "redirect:/login";
        }
        List<Notification> unread = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(currentUser.getId())
                .stream().filter(item -> item.getReadAt() == null)
                .peek(item -> item.setReadAt(LocalDateTime.now())).toList();
        if (!unread.isEmpty()) {
            notificationRepository.saveAll(unread);
        }
        return "redirect:/notifications";
    }
}

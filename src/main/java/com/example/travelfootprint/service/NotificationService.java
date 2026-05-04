package com.example.travelfootprint.service;

import com.example.travelfootprint.model.Notification;
import com.example.travelfootprint.model.NotificationType;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.NotificationRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public void notify(User recipient, User actor, NotificationType type, String message, String linkPath) {
        if (recipient == null) {
            return;
        }
        if (actor != null && recipient.getId().equals(actor.getId())) {
            return;
        }

        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setActor(actor);
        notification.setType(type);
        notification.setMessage(message);
        notification.setLinkPath(linkPath);
        notificationRepository.save(notification);
    }

    public void markAllAsRead(User user) {
        if (user == null) {
            return;
        }
        notificationRepository.findByRecipientIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(notification -> notification.getReadAt() == null)
                .forEach(notification -> notification.setReadAt(LocalDateTime.now()));
    }
}

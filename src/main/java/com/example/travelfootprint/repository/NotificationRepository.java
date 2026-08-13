package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.Notification;
import com.example.travelfootprint.model.NotificationType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    boolean existsByRecipientIdAndTypeAndLinkPath(Long recipientId, NotificationType type, String linkPath);
}

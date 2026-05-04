package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.PrivateMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivateMessageRepository extends JpaRepository<PrivateMessage, Long> {

    List<PrivateMessage> findBySenderIdAndReceiverIdOrSenderIdAndReceiverIdOrderByCreatedAtAsc(
            Long senderId, Long receiverId, Long reverseSenderId, Long reverseReceiverId);

    List<PrivateMessage> findBySenderIdOrReceiverIdOrderByCreatedAtDesc(Long senderId, Long receiverId);

    List<PrivateMessage> findByReceiverIdOrderByCreatedAtDesc(Long receiverId);

    long countByReceiverIdAndReadAtIsNull(Long receiverId);
}

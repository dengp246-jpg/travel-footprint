package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.NotificationType;
import com.example.travelfootprint.model.PrivateMessage;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.PrivateMessageRepository;
import com.example.travelfootprint.repository.UserRepository;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class MessageController {

    private final PrivateMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;

    public MessageController(
            PrivateMessageRepository messageRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService,
            NotificationService notificationService) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.notificationService = notificationService;
    }

    @GetMapping("/messages")
    public String messages(
            @RequestParam(required = false) Long userId,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先登录，再查看私信。");
            return "redirect:/login";
        }

        List<PrivateMessage> allMessages = messageRepository.findBySenderIdOrReceiverIdOrderByCreatedAtDesc(
                currentUser.getId(), currentUser.getId());
        Map<Long, PrivateMessage> latestByPartner = new LinkedHashMap<>();
        for (PrivateMessage message : allMessages) {
            User partner = message.getSender().getId().equals(currentUser.getId()) ? message.getReceiver() : message.getSender();
            latestByPartner.putIfAbsent(partner.getId(), message);
        }

        Map<Long, Long> unreadByPartner = allMessages.stream()
                .filter(message -> message.getReceiver().getId().equals(currentUser.getId()) && message.getReadAt() == null)
                .collect(java.util.stream.Collectors.groupingBy(
                        message -> message.getSender().getId(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.counting()));

        List<User> contacts = latestByPartner.keySet().stream()
                .map(id -> userRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(User::getNickname))
                .toList();
        List<User> allUsers = userRepository.findByIdNotOrderByNicknameAsc(currentUser.getId());

        User selectedUser = userId == null
                ? (!contacts.isEmpty() ? contacts.get(0) : (allUsers.isEmpty() ? null : allUsers.get(0)))
                : userRepository.findById(userId).orElse(null);

        List<PrivateMessage> thread = selectedUser == null
                ? List.of()
                : messageRepository.findBySenderIdAndReceiverIdOrSenderIdAndReceiverIdOrderByCreatedAtAsc(
                        currentUser.getId(), selectedUser.getId(), selectedUser.getId(), currentUser.getId());

        List<PrivateMessage> unreadMessages = thread.stream()
                .filter(message -> message.getReceiver().getId().equals(currentUser.getId()) && message.getReadAt() == null)
                .peek(message -> message.setReadAt(LocalDateTime.now()))
                .toList();
        if (!unreadMessages.isEmpty()) {
            messageRepository.saveAll(unreadMessages);
        }

        model.addAttribute("contacts", contacts);
        model.addAttribute("allUsers", allUsers);
        model.addAttribute("selectedUser", selectedUser);
        model.addAttribute("thread", thread);
        model.addAttribute("unreadByPartner", unreadByPartner);
        return "messages";
    }

    @PostMapping("/messages/{userId}")
    public String sendMessage(
            @PathVariable Long userId,
            @RequestParam String content,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先登录，再发送私信。");
            return "redirect:/login";
        }
        if (content.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "私信内容不能为空。");
            return "redirect:/messages?userId=" + userId;
        }
        if (currentUser.getId().equals(userId)) {
            redirectAttributes.addFlashAttribute("errorMessage", "不能给自己发私信。");
            return "redirect:/messages";
        }

        User receiver = userRepository.findById(userId).orElseThrow();
        PrivateMessage message = new PrivateMessage();
        message.setSender(currentUser);
        message.setReceiver(receiver);
        message.setContent(content.trim());
        messageRepository.save(message);

        notificationService.notify(
                receiver,
                currentUser,
                NotificationType.MESSAGE,
                currentUser.getNickname() + " 给你发送了一条私信",
                "/messages?userId=" + currentUser.getId());
        return "redirect:/messages?userId=" + userId;
    }
}

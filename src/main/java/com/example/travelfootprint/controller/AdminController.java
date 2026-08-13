package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.Comment;
import com.example.travelfootprint.model.ContentReviewStatus;
import com.example.travelfootprint.model.NotificationType;
import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.CommentRepository;
import com.example.travelfootprint.repository.TravelPostRepository;
import com.example.travelfootprint.repository.UserRepository;
import com.example.travelfootprint.service.ContentVisibilityService;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminController {

    private final TravelPostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final ContentVisibilityService contentVisibilityService;
    private final NotificationService notificationService;

    public AdminController(
            TravelPostRepository postRepository,
            CommentRepository commentRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService,
            ContentVisibilityService contentVisibilityService,
            NotificationService notificationService) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.contentVisibilityService = contentVisibilityService;
        this.notificationService = notificationService;
    }

    @GetMapping("/admin")
    public String dashboard(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        User admin = currentUserService.getCurrentUser(session);
        if (!contentVisibilityService.isAdmin(admin)) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先使用管理员账号登录。");
            return "redirect:/";
        }

        List<TravelPost> posts = postRepository.findAllByOrderByCreatedAtDesc();
        List<Comment> comments = commentRepository.findAll().stream()
                .sorted(Comparator.comparing(Comment::getCreatedAt).reversed())
                .toList();
        List<User> users = userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getJoinedAt).reversed())
                .toList();

        model.addAttribute("pendingPosts", posts.stream()
                .filter(post -> post.getReviewStatus() == ContentReviewStatus.PENDING)
                .limit(10)
                .toList());
        model.addAttribute("rejectedPosts", posts.stream()
                .filter(post -> post.getReviewStatus() == ContentReviewStatus.REJECTED)
                .limit(6)
                .toList());
        model.addAttribute("pendingComments", comments.stream()
                .filter(comment -> comment.getReviewStatus() == ContentReviewStatus.PENDING)
                .limit(12)
                .toList());
        model.addAttribute("disabledUsers", users.stream()
                .filter(user -> !user.isEnabled())
                .toList());
        model.addAttribute("recentUsers", users.stream().limit(12).toList());
        model.addAttribute("pendingPostCount", posts.stream()
                .filter(post -> post.getReviewStatus() == ContentReviewStatus.PENDING)
                .count());
        model.addAttribute("pendingCommentCount", comments.stream()
                .filter(comment -> comment.getReviewStatus() == ContentReviewStatus.PENDING)
                .count());
        model.addAttribute("disabledUserCount", users.stream()
                .filter(user -> !user.isEnabled())
                .count());
        model.addAttribute("approvedPostCount", posts.stream()
                .filter(contentVisibilityService::isApproved)
                .count());
        return "admin";
    }

    @PostMapping("/admin/posts/{id}/approve")
    public String approvePost(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!ensureAdmin(session, redirectAttributes)) {
            return "redirect:/";
        }
        TravelPost post = postRepository.findById(id).orElseThrow();
        post.setReviewStatus(ContentReviewStatus.APPROVED);
        postRepository.save(post);
        redirectAttributes.addFlashAttribute("successMessage", "足迹已审核通过。");
        return "redirect:/admin";
    }

    @PostMapping("/admin/posts/{id}/reject")
    public String rejectPost(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!ensureAdmin(session, redirectAttributes)) {
            return "redirect:/";
        }
        TravelPost post = postRepository.findById(id).orElseThrow();
        post.setReviewStatus(ContentReviewStatus.REJECTED);
        postRepository.save(post);
        redirectAttributes.addFlashAttribute("successMessage", "足迹已标记为未通过。");
        return "redirect:/admin";
    }

    @PostMapping("/admin/comments/{id}/approve")
    public String approveComment(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User admin = currentUserService.getCurrentUser(session);
        if (!contentVisibilityService.isAdmin(admin)) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先使用管理员账号登录。");
            return "redirect:/";
        }
        Comment comment = commentRepository.findById(id).orElseThrow();
        boolean sendNotification = comment.getReviewStatus() != ContentReviewStatus.APPROVED;
        comment.setReviewStatus(ContentReviewStatus.APPROVED);
        commentRepository.save(comment);
        if (sendNotification) {
            notifyCommentApproved(comment);
        }
        redirectAttributes.addFlashAttribute("successMessage", "评论已审核通过。");
        return "redirect:/admin";
    }

    @PostMapping("/admin/comments/{id}/reject")
    public String rejectComment(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!ensureAdmin(session, redirectAttributes)) {
            return "redirect:/";
        }
        Comment comment = commentRepository.findById(id).orElseThrow();
        comment.setReviewStatus(ContentReviewStatus.REJECTED);
        commentRepository.save(comment);
        redirectAttributes.addFlashAttribute("successMessage", "评论已标记为未通过。");
        return "redirect:/admin";
    }

    @PostMapping("/admin/users/{id}/disable")
    public String disableUser(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User admin = currentUserService.getCurrentUser(session);
        if (!contentVisibilityService.isAdmin(admin)) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先使用管理员账号登录。");
            return "redirect:/";
        }
        User targetUser = userRepository.findById(id).orElseThrow();
        if (targetUser.getId().equals(admin.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "不能禁用当前管理员账号。");
            return "redirect:/admin";
        }
        if (targetUser.isAdmin()) {
            redirectAttributes.addFlashAttribute("errorMessage", "请保留管理员账号可用。");
            return "redirect:/admin";
        }
        targetUser.setEnabled(false);
        userRepository.save(targetUser);
        redirectAttributes.addFlashAttribute("successMessage", "用户已禁用。");
        return "redirect:/admin";
    }

    @PostMapping("/admin/users/{id}/enable")
    public String enableUser(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        if (!ensureAdmin(session, redirectAttributes)) {
            return "redirect:/";
        }
        User targetUser = userRepository.findById(id).orElseThrow();
        targetUser.setEnabled(true);
        userRepository.save(targetUser);
        redirectAttributes.addFlashAttribute("successMessage", "用户已恢复。");
        return "redirect:/admin";
    }

    private boolean ensureAdmin(HttpSession session, RedirectAttributes redirectAttributes) {
        User admin = currentUserService.getCurrentUser(session);
        if (contentVisibilityService.isAdmin(admin)) {
            return true;
        }
        redirectAttributes.addFlashAttribute("errorMessage", "请先使用管理员账号登录。");
        return false;
    }

    private void notifyCommentApproved(Comment comment) {
        if (comment.getParentComment() != null) {
            notificationService.notify(
                    comment.getParentComment().getAuthor(),
                    comment.getAuthor(),
                    NotificationType.REPLY,
                    comment.getAuthor().getNickname() + " 回复了你的评论",
                    "/posts/" + comment.getPost().getId());
            return;
        }

        notificationService.notify(
                comment.getPost().getAuthor(),
                comment.getAuthor(),
                NotificationType.COMMENT,
                comment.getAuthor().getNickname() + " 评论了你的足迹",
                "/posts/" + comment.getPost().getId());
    }
}

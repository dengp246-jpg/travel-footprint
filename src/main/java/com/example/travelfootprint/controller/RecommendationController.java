package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.RecommendationDismissal;
import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.RecommendationDismissalRepository;
import com.example.travelfootprint.repository.TravelPostRepository;
import com.example.travelfootprint.service.ContentVisibilityService;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.PersonalizedRecommendationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RecommendationController {

    private final CurrentUserService currentUserService;
    private final PersonalizedRecommendationService recommendationService;
    private final TravelPostRepository postRepository;
    private final RecommendationDismissalRepository dismissalRepository;
    private final ContentVisibilityService visibilityService;

    public RecommendationController(
            CurrentUserService currentUserService,
            PersonalizedRecommendationService recommendationService,
            TravelPostRepository postRepository,
            RecommendationDismissalRepository dismissalRepository,
            ContentVisibilityService visibilityService) {
        this.currentUserService = currentUserService;
        this.recommendationService = recommendationService;
        this.postRepository = postRepository;
        this.dismissalRepository = dismissalRepository;
        this.visibilityService = visibilityService;
    }

    @GetMapping("/recommendations")
    public String recommendations(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        User user = currentUserService.getCurrentUser(session);
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "登录后才能获得个性化旅行推荐。");
            return "redirect:/login";
        }
        model.addAttribute("recommendationView", recommendationService.build(user));
        return "travel-recommendations";
    }

    @PostMapping("/recommendations/{postId}/dismiss")
    public String dismiss(@PathVariable Long postId, HttpSession session) {
        User user = currentUserService.getCurrentUser(session);
        if (user == null) return "redirect:/login";
        TravelPost post = postRepository.findById(postId).orElseThrow();
        if (visibilityService.isPublicPost(post) && !dismissalRepository.existsByUserIdAndPostId(user.getId(), postId)) {
            RecommendationDismissal dismissal = new RecommendationDismissal();
            dismissal.setUser(user);
            dismissal.setPost(post);
            dismissalRepository.save(dismissal);
        }
        return "redirect:/recommendations";
    }

    @PostMapping("/recommendations/reset")
    public String reset(HttpSession session) {
        User user = currentUserService.getCurrentUser(session);
        if (user == null) return "redirect:/login";
        dismissalRepository.deleteByUserId(user.getId());
        return "redirect:/recommendations";
    }
}

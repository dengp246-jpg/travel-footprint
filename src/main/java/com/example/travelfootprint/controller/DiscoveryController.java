package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.User;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.TravelCompanionService;
import com.example.travelfootprint.service.ViewDataService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class DiscoveryController {

    private final CurrentUserService currentUserService;
    private final TravelCompanionService companionService;
    private final ViewDataService viewDataService;

    public DiscoveryController(
            CurrentUserService currentUserService,
            TravelCompanionService companionService,
            ViewDataService viewDataService) {
        this.currentUserService = currentUserService;
        this.companionService = companionService;
        this.viewDataService = viewDataService;
    }

    @GetMapping("/discover")
    public String discover(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先登录，再发现旅行同好。");
            return "redirect:/login";
        }
        TravelCompanionService.DiscoveryView discovery = companionService.build(currentUser);
        model.addAttribute("discovery", discovery);
        model.addAttribute("likeCounts", viewDataService.likeCounts(discovery.followingPosts()));
        model.addAttribute("commentCounts", viewDataService.commentCounts(discovery.followingPosts()));
        return "discover";
    }
}

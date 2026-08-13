package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.User;
import com.example.travelfootprint.service.AdvancedTravelInsightService;
import com.example.travelfootprint.service.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdvancedInsightController {

    private final CurrentUserService currentUserService;
    private final AdvancedTravelInsightService insightService;

    public AdvancedInsightController(CurrentUserService currentUserService, AdvancedTravelInsightService insightService) {
        this.currentUserService = currentUserService;
        this.insightService = insightService;
    }

    @GetMapping("/insights")
    public String insights(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        User user = currentUserService.getCurrentUser(session);
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "登录后才能查看个人旅行洞察。");
            return "redirect:/login";
        }
        model.addAttribute("insight", insightService.build(user));
        return "advanced-insights";
    }
}

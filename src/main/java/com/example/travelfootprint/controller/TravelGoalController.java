package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.TravelGoal;
import com.example.travelfootprint.model.TravelGoalType;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.TravelGoalRepository;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.TravelGoalService;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TravelGoalController {
    private final CurrentUserService currentUserService;
    private final TravelGoalRepository goalRepository;
    private final TravelGoalService goalService;

    public TravelGoalController(CurrentUserService currentUserService, TravelGoalRepository goalRepository,
                                TravelGoalService goalService) {
        this.currentUserService = currentUserService;
        this.goalRepository = goalRepository;
        this.goalService = goalService;
    }

    @GetMapping("/goals")
    public String goals(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        User user = currentUserService.getCurrentUser(session);
        if (user == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先登录，再设置旅行目标。");
            return "redirect:/login";
        }
        model.addAttribute("goals", goalService.goals(user));
        model.addAttribute("goalTypes", TravelGoalType.values());
        model.addAttribute("currentYear", LocalDate.now().getYear());
        return "travel-goals";
    }

    @PostMapping("/goals")
    public String create(@RequestParam String title, @RequestParam TravelGoalType type,
                         @RequestParam int targetValue, @RequestParam int targetYear,
                         HttpSession session, RedirectAttributes redirectAttributes) {
        User user = currentUserService.getCurrentUser(session);
        if (user == null) return "redirect:/login";
        String normalizedTitle = title == null ? "" : title.trim();
        int currentYear = LocalDate.now().getYear();
        if (normalizedTitle.isBlank() || normalizedTitle.length() > 100 || targetValue < 1 || targetValue > 10000
                || targetYear < 2000 || targetYear > currentYear + 5) {
            redirectAttributes.addFlashAttribute("errorMessage", "请填写有效的目标名称、年份和目标数值。");
            return "redirect:/goals";
        }
        TravelGoal goal = new TravelGoal();
        goal.setOwner(user); goal.setTitle(normalizedTitle); goal.setType(type);
        goal.setTargetValue(targetValue); goal.setTargetYear(targetYear);
        goalRepository.save(goal);
        redirectAttributes.addFlashAttribute("successMessage", "旅行目标已创建，进度会随足迹自动更新。");
        return "redirect:/goals";
    }

    @PostMapping("/goals/{id}/delete")
    public String delete(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = currentUserService.getCurrentUser(session);
        if (user == null) return "redirect:/login";
        TravelGoal goal = goalRepository.findById(id).orElseThrow();
        if (!goal.getOwner().getId().equals(user.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "只能删除自己的旅行目标。");
            return "redirect:/goals";
        }
        goalRepository.delete(goal);
        redirectAttributes.addFlashAttribute("successMessage", "旅行目标已删除。");
        return "redirect:/goals";
    }
}

package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.TripPlan;
import com.example.travelfootprint.model.TripPlanStatus;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.TripPlanRepository;
import com.example.travelfootprint.service.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PlanController {

    private final TripPlanRepository tripPlanRepository;
    private final CurrentUserService currentUserService;

    public PlanController(TripPlanRepository tripPlanRepository, CurrentUserService currentUserService) {
        this.tripPlanRepository = tripPlanRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/plans")
    public String plans(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先登录，再管理行程计划。");
            return "redirect:/login";
        }

        model.addAttribute("plans", tripPlanRepository.findByOwnerIdOrderByStartDateAscCreatedAtDesc(currentUser.getId()));
        model.addAttribute("statuses", TripPlanStatus.values());
        return "plans";
    }

    @PostMapping("/plans")
    public String createPlan(
            @RequestParam String title,
            @RequestParam String destination,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String budget,
            @RequestParam TripPlanStatus status,
            @RequestParam(required = false) String notes,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先登录，再创建行程计划。");
            return "redirect:/login";
        }
        if (title.isBlank() || destination.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "计划标题和目的地不能为空。");
            return "redirect:/plans";
        }

        TripPlan plan = new TripPlan();
        plan.setOwner(currentUser);
        plan.setTitle(title.trim());
        plan.setDestination(destination.trim());
        plan.setStatus(status);
        plan.setNotes(notes == null ? "" : notes.trim());

        try {
            if (startDate != null && !startDate.isBlank()) {
                plan.setStartDate(LocalDate.parse(startDate));
            }
            if (endDate != null && !endDate.isBlank()) {
                plan.setEndDate(LocalDate.parse(endDate));
            }
        } catch (DateTimeParseException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", "行程日期格式不正确。");
            return "redirect:/plans";
        }

        if (budget != null && !budget.isBlank()) {
            try {
                plan.setBudget(new BigDecimal(budget.trim()));
            } catch (NumberFormatException exception) {
                redirectAttributes.addFlashAttribute("errorMessage", "预算格式不正确。");
                return "redirect:/plans";
            }
        }

        tripPlanRepository.save(plan);
        redirectAttributes.addFlashAttribute("successMessage", "行程计划已创建。");
        return "redirect:/plans";
    }

    @PostMapping("/plans/{id}/delete")
    public String deletePlan(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先登录，再操作行程计划。");
            return "redirect:/login";
        }

        TripPlan plan = tripPlanRepository.findById(id).orElseThrow();
        if (!plan.getOwner().getId().equals(currentUser.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "只能删除自己的行程计划。");
            return "redirect:/plans";
        }
        tripPlanRepository.delete(plan);
        redirectAttributes.addFlashAttribute("successMessage", "行程计划已删除。");
        return "redirect:/plans";
    }
}

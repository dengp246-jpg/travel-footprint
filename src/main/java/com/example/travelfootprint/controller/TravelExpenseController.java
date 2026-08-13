package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.ExpenseCategory;
import com.example.travelfootprint.model.TravelExpense;
import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.TripPlan;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.TravelExpenseRepository;
import com.example.travelfootprint.repository.TravelPostRepository;
import com.example.travelfootprint.repository.TripPlanRepository;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.TravelExpenseService;
import com.example.travelfootprint.service.TripPlanAccessService;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TravelExpenseController {

    private final CurrentUserService currentUserService;
    private final TravelExpenseService expenseService;
    private final TravelExpenseRepository expenseRepository;
    private final TripPlanRepository tripPlanRepository;
    private final TravelPostRepository postRepository;
    private final TripPlanAccessService planAccessService;

    public TravelExpenseController(
            CurrentUserService currentUserService,
            TravelExpenseService expenseService,
            TravelExpenseRepository expenseRepository,
            TripPlanRepository tripPlanRepository,
            TravelPostRepository postRepository,
            TripPlanAccessService planAccessService) {
        this.currentUserService = currentUserService;
        this.expenseService = expenseService;
        this.expenseRepository = expenseRepository;
        this.tripPlanRepository = tripPlanRepository;
        this.postRepository = postRepository;
        this.planAccessService = planAccessService;
    }

    @GetMapping("/expenses")
    public String expenses(
            @RequestParam(required = false) String month,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先登录，再使用旅行账本。");
            return "redirect:/login";
        }
        YearMonth selectedMonth = parseMonth(month);
        model.addAttribute("expenseView", expenseService.build(currentUser, selectedMonth));
        model.addAttribute("selectedMonth", selectedMonth.toString());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("expenseCategories", ExpenseCategory.values());
        model.addAttribute("availablePlans", planAccessService.visiblePlans(currentUser));
        model.addAttribute("availablePosts", postRepository.findByAuthorIdOrderByCreatedAtDesc(currentUser.getId()).stream().limit(40).toList());
        return "travel-expenses";
    }

    @PostMapping("/expenses")
    public String createExpense(
            @RequestParam String amount,
            @RequestParam ExpenseCategory category,
            @RequestParam String occurredOn,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) Long tripPlanId,
            @RequestParam(required = false) Long travelPostId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) return "redirect:/login";

        try {
            BigDecimal parsedAmount = new BigDecimal(amount.trim()).setScale(2);
            LocalDate parsedDate = LocalDate.parse(occurredOn);
            if (parsedAmount.signum() <= 0 || parsedAmount.compareTo(new BigDecimal("999999999.99")) > 0) {
                throw new IllegalArgumentException("金额必须大于 0 且不能超过 999999999.99。");
            }
            String normalizedNote = note == null ? "" : note.trim();
            if (normalizedNote.length() > 240) throw new IllegalArgumentException("备注最多 240 个字。");
            TripPlan plan = ownedPlan(tripPlanId, currentUser);
            TravelPost post = ownedPost(travelPostId, currentUser);
            if (tripPlanId != null && plan == null) throw new IllegalArgumentException("只能关联自己可编辑的行程计划。");
            if (travelPostId != null && post == null) throw new IllegalArgumentException("只能关联自己的旅行足迹。");

            TravelExpense expense = new TravelExpense();
            expense.setOwner(currentUser);
            expense.setAmount(parsedAmount);
            expense.setCategory(category);
            expense.setOccurredOn(parsedDate);
            expense.setNote(normalizedNote);
            expense.setTripPlan(plan);
            expense.setTravelPost(post);
            expenseRepository.save(expense);
            redirectAttributes.addFlashAttribute("successMessage", "旅行支出已记入账本。");
            return "redirect:/expenses?month=" + YearMonth.from(parsedDate);
        } catch (NumberFormatException | DateTimeParseException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", "金额或日期格式不正确。");
        } catch (ArithmeticException | IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/expenses";
    }

    @PostMapping("/expenses/{id}/delete")
    public String deleteExpense(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) return "redirect:/login";
        TravelExpense expense = expenseRepository.findById(id).orElseThrow();
        if (!expense.getOwner().getId().equals(currentUser.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "只能删除自己的账本记录。");
            return "redirect:/expenses";
        }
        YearMonth month = YearMonth.from(expense.getOccurredOn());
        expenseRepository.delete(expense);
        redirectAttributes.addFlashAttribute("successMessage", "账本记录已删除。");
        return "redirect:/expenses?month=" + month;
    }

    private YearMonth parseMonth(String value) {
        try {
            return value == null || value.isBlank() ? YearMonth.now() : YearMonth.parse(value);
        } catch (DateTimeParseException exception) {
            return YearMonth.now();
        }
    }

    private TripPlan ownedPlan(Long id, User user) {
        return id == null ? null : tripPlanRepository.findById(id)
                .filter(plan -> planAccessService.canEdit(plan, user)).orElse(null);
    }

    private TravelPost ownedPost(Long id, User user) {
        return id == null ? null : postRepository.findById(id)
                .filter(post -> post.getAuthor().getId().equals(user.getId())).orElse(null);
    }
}

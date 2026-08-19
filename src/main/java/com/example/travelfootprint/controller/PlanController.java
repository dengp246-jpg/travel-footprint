package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.TripPlan;
import com.example.travelfootprint.model.TripPlanMember;
import com.example.travelfootprint.model.TripPlanMemberStatus;
import com.example.travelfootprint.model.TripPlanStatus;
import com.example.travelfootprint.model.NotificationType;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.TripPlanMemberRepository;
import com.example.travelfootprint.repository.TripPlanRepository;
import com.example.travelfootprint.repository.TravelPostRepository;
import com.example.travelfootprint.repository.TravelExpenseRepository;
import com.example.travelfootprint.repository.UserRepository;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.NotificationService;
import com.example.travelfootprint.service.PlanReminderService;
import com.example.travelfootprint.service.TripPlanAccessService;
import com.example.travelfootprint.service.TripPlanWorkspaceService;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final TravelPostRepository travelPostRepository;
    private final TravelExpenseRepository expenseRepository;
    private final CurrentUserService currentUserService;
    private final TripPlanMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final TripPlanAccessService planAccessService;
    private final NotificationService notificationService;
    private final PlanReminderService reminderService;
    private final TripPlanWorkspaceService workspaceService;

    public PlanController(
            TripPlanRepository tripPlanRepository,
            TravelPostRepository travelPostRepository,
            TravelExpenseRepository expenseRepository,
            CurrentUserService currentUserService,
            TripPlanMemberRepository memberRepository,
            UserRepository userRepository,
            TripPlanAccessService planAccessService,
            NotificationService notificationService,
            PlanReminderService reminderService,
            TripPlanWorkspaceService workspaceService) {
        this.tripPlanRepository = tripPlanRepository;
        this.travelPostRepository = travelPostRepository;
        this.expenseRepository = expenseRepository;
        this.currentUserService = currentUserService;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.planAccessService = planAccessService;
        this.notificationService = notificationService;
        this.reminderService = reminderService;
        this.workspaceService = workspaceService;
    }

    @GetMapping("/plans")
    public String plans(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先登录，再管理行程计划。");
            return "redirect:/login";
        }

        List<TripPlan> plans = planAccessService.visiblePlans(currentUser);
        Map<Long, List<com.example.travelfootprint.model.TravelPost>> planPosts = new LinkedHashMap<>();
        Map<Long, PlanProgress> planProgress = new LinkedHashMap<>();
        Map<Long, BigDecimal> planSpent = new LinkedHashMap<>();
        Map<Long, List<TripPlanMember>> planMembers = new LinkedHashMap<>();
        Map<Long, List<TripPlanMember>> planInvitations = new LinkedHashMap<>();
        Map<Long, Boolean> ownedPlans = new LinkedHashMap<>();
        Map<Long, BigDecimal> expenseTotals = plans.isEmpty() ? Map.of() : expenseRepository
                .sumByTripPlanIds(plans.stream().map(TripPlan::getId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        TravelExpenseRepository.PlanExpenseTotal::getPlanId,
                        TravelExpenseRepository.PlanExpenseTotal::getTotal));
        plans.forEach(plan -> {
            List<com.example.travelfootprint.model.TravelPost> posts =
                    travelPostRepository.findByTripPlanIdOrderByTravelDateAscCreatedAtAsc(plan.getId());
            planPosts.put(plan.getId(), posts);
            planProgress.put(plan.getId(), progress(plan, posts));
            planSpent.put(plan.getId(), expenseTotals.getOrDefault(plan.getId(), BigDecimal.ZERO));
            planMembers.put(plan.getId(), planAccessService.acceptedMembers(plan));
            planInvitations.put(plan.getId(), planAccessService.allInvitations(plan));
            ownedPlans.put(plan.getId(), planAccessService.isOwner(plan, currentUser));
        });
        model.addAttribute("plans", plans);
        model.addAttribute("planPosts", planPosts);
        model.addAttribute("planProgress", planProgress);
        model.addAttribute("planSpent", planSpent);
        model.addAttribute("planMembers", planMembers);
        model.addAttribute("planInvitations", planInvitations);
        model.addAttribute("ownedPlans", ownedPlans);
        model.addAttribute("pendingInvitations", planAccessService.pendingInvitations(currentUser));
        model.addAttribute("upcomingPlans", reminderService.syncAndList(currentUser));
        model.addAttribute("statuses", TripPlanStatus.values());
        return "plans";
    }

    @PostMapping("/plans/{id}/invite")
    public String inviteCompanion(
            @PathVariable Long id,
            @RequestParam String username,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            return "redirect:/login";
        }
        TripPlan plan = tripPlanRepository.findById(id).orElseThrow();
        if (!planAccessService.isOwner(plan, currentUser)) {
            redirectAttributes.addFlashAttribute("errorMessage", "只有计划创建者可以邀请同行者。");
            return "redirect:/plans";
        }
        User invitedUser = userRepository.findByUsername(username.trim()).orElse(null);
        if (invitedUser == null || !invitedUser.isEnabled()) {
            redirectAttributes.addFlashAttribute("errorMessage", "没有找到可邀请的用户，请检查用户名。");
            return "redirect:/plans#plan-" + id;
        }
        if (invitedUser.getId().equals(currentUser.getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "你已经是这个计划的创建者。");
            return "redirect:/plans#plan-" + id;
        }

        TripPlanMember invitation = memberRepository.findByTripPlanIdAndUserId(id, invitedUser.getId())
                .orElseGet(TripPlanMember::new);
        if (invitation.getId() != null && invitation.getStatus() != TripPlanMemberStatus.DECLINED) {
            redirectAttributes.addFlashAttribute("errorMessage", "该用户已经收到邀请或已经加入计划。");
            return "redirect:/plans#plan-" + id;
        }
        invitation.setTripPlan(plan);
        invitation.setUser(invitedUser);
        invitation.setInvitedBy(currentUser);
        invitation.setStatus(TripPlanMemberStatus.PENDING);
        invitation.setRespondedAt(null);
        memberRepository.save(invitation);
        notificationService.notify(invitedUser, currentUser, NotificationType.PLAN_INVITE,
                currentUser.getNickname() + "邀请你加入行程「" + plan.getTitle() + "」", "/plans");
        redirectAttributes.addFlashAttribute("successMessage", "同行邀请已发送。");
        return "redirect:/plans#plan-" + id;
    }

    @PostMapping("/plans/invitations/{id}/respond")
    public String respondInvitation(
            @PathVariable Long id,
            @RequestParam boolean accept,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            return "redirect:/login";
        }
        TripPlanMember invitation = memberRepository.findById(id).orElseThrow();
        if (!invitation.getUser().getId().equals(currentUser.getId())
                || invitation.getStatus() != TripPlanMemberStatus.PENDING) {
            redirectAttributes.addFlashAttribute("errorMessage", "该邀请无法处理。");
            return "redirect:/plans";
        }
        invitation.setStatus(accept ? TripPlanMemberStatus.ACCEPTED : TripPlanMemberStatus.DECLINED);
        invitation.setRespondedAt(java.time.LocalDateTime.now());
        memberRepository.save(invitation);
        notificationService.notify(invitation.getTripPlan().getOwner(), currentUser, NotificationType.PLAN_INVITE,
                currentUser.getNickname() + (accept ? "已加入" : "已婉拒")
                        + "行程「" + invitation.getTripPlan().getTitle() + "」",
                "/plans#plan-" + invitation.getTripPlan().getId());
        redirectAttributes.addFlashAttribute("successMessage", accept ? "已加入同行计划。" : "已拒绝该邀请。");
        return "redirect:/plans";
    }

    @PostMapping("/plans/members/{id}/remove")
    public String removeCompanion(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            return "redirect:/login";
        }
        TripPlanMember membership = memberRepository.findById(id).orElseThrow();
        boolean ownerRemoving = planAccessService.isOwner(membership.getTripPlan(), currentUser);
        boolean leavingSelf = membership.getUser().getId().equals(currentUser.getId());
        if (!ownerRemoving && !leavingSelf) {
            redirectAttributes.addFlashAttribute("errorMessage", "你无权移除这位同行者。");
            return "redirect:/plans";
        }
        Long planId = membership.getTripPlan().getId();
        memberRepository.delete(membership);
        redirectAttributes.addFlashAttribute("successMessage", leavingSelf && !ownerRemoving
                ? "你已退出该同行计划。" : "同行者已从计划中移除。");
        return "redirect:/plans#plan-" + planId;
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
        List<com.example.travelfootprint.model.TravelPost> linkedPosts =
                travelPostRepository.findByTripPlanIdOrderByTravelDateAscCreatedAtAsc(plan.getId());
        linkedPosts.forEach(post -> post.setTripPlan(null));
        travelPostRepository.saveAll(linkedPosts);
        List<com.example.travelfootprint.model.TravelExpense> linkedExpenses = expenseRepository.findByTripPlanId(plan.getId());
        linkedExpenses.forEach(expense -> expense.setTripPlan(null));
        expenseRepository.saveAll(linkedExpenses);
        workspaceService.cleanupBeforePlanDeletion(plan);
        memberRepository.deleteByTripPlanId(plan.getId());
        tripPlanRepository.delete(plan);
        redirectAttributes.addFlashAttribute("successMessage", "行程计划已删除。");
        return "redirect:/plans";
    }

    private PlanProgress progress(TripPlan plan, List<com.example.travelfootprint.model.TravelPost> posts) {
        long completedDays = posts.stream().map(com.example.travelfootprint.model.TravelPost::getTravelDate).distinct().count();
        long plannedDays = 0;
        if (plan.getStartDate() != null && plan.getEndDate() != null && !plan.getEndDate().isBefore(plan.getStartDate())) {
            plannedDays = ChronoUnit.DAYS.between(plan.getStartDate(), plan.getEndDate()) + 1;
        }
        long denominator = plannedDays > 0 ? plannedDays : Math.max(1, completedDays);
        long percent = Math.min(100, Math.round(completedDays * 100.0 / denominator));
        if (plan.getStatus() == TripPlanStatus.FINISHED) {
            percent = 100;
        }
        return new PlanProgress(posts.size(), completedDays, plannedDays, percent);
    }

    public record PlanProgress(long postCount, long completedDays, long plannedDays, long percent) {
    }
}

package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.TripChecklistCategory;
import com.example.travelfootprint.model.TripChecklistItem;
import com.example.travelfootprint.model.TripPlan;
import com.example.travelfootprint.model.TripPlanActivity;
import com.example.travelfootprint.model.TripPlanStatus;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.TravelExpenseRepository;
import com.example.travelfootprint.repository.TravelPostRepository;
import com.example.travelfootprint.repository.TripChecklistItemRepository;
import com.example.travelfootprint.repository.TripPlanActivityRepository;
import com.example.travelfootprint.repository.TripPlanRepository;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.SmartItineraryService;
import com.example.travelfootprint.service.TripPlanAccessService;
import com.example.travelfootprint.service.TripPlanWorkspaceService;
import com.example.travelfootprint.service.TripReadinessService;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TripWorkspaceController {

    private final TripPlanRepository planRepository;
    private final TripPlanActivityRepository activityRepository;
    private final TripChecklistItemRepository checklistRepository;
    private final TravelPostRepository postRepository;
    private final TravelExpenseRepository expenseRepository;
    private final CurrentUserService currentUserService;
    private final TripPlanAccessService accessService;
    private final TripPlanWorkspaceService workspaceService;
    private final SmartItineraryService smartItineraryService;
    private final TripReadinessService readinessService;

    public TripWorkspaceController(
            TripPlanRepository planRepository,
            TripPlanActivityRepository activityRepository,
            TripChecklistItemRepository checklistRepository,
            TravelPostRepository postRepository,
            TravelExpenseRepository expenseRepository,
            CurrentUserService currentUserService,
            TripPlanAccessService accessService,
            TripPlanWorkspaceService workspaceService,
            SmartItineraryService smartItineraryService,
            TripReadinessService readinessService) {
        this.planRepository = planRepository;
        this.activityRepository = activityRepository;
        this.checklistRepository = checklistRepository;
        this.postRepository = postRepository;
        this.expenseRepository = expenseRepository;
        this.currentUserService = currentUserService;
        this.accessService = accessService;
        this.workspaceService = workspaceService;
        this.smartItineraryService = smartItineraryService;
        this.readinessService = readinessService;
    }

    @GetMapping("/plans/{id}")
    public String workspace(
            @PathVariable Long id,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        TripPlan plan = planRepository.findById(id).orElseThrow();
        if (!accessService.canView(plan, currentUser)) {
            redirectAttributes.addFlashAttribute("errorMessage", "你无权查看这个行程工作台。");
            return currentUser == null ? "redirect:/login" : "redirect:/plans";
        }
        addWorkspaceModel(plan, currentUser, model);
        return "trip-workspace";
    }

    @PostMapping("/plans/{id}/update")
    public String updatePlan(
            @PathVariable Long id,
            @RequestParam String title,
            @RequestParam String destination,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) BigDecimal budget,
            @RequestParam TripPlanStatus status,
            @RequestParam(required = false) String notes,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        TripPlan plan = planRepository.findById(id).orElseThrow();
        if (!accessService.isOwner(plan, currentUser)) {
            redirectAttributes.addFlashAttribute("errorMessage", "只有计划创建者可以修改基础信息。");
            return "redirect:/plans/" + id;
        }
        if (title.isBlank() || destination.isBlank() || title.length() > 100 || destination.length() > 100
                || (notes != null && notes.trim().length() > 1200)
                || (startDate != null && endDate != null && endDate.isBefore(startDate))) {
            redirectAttributes.addFlashAttribute("errorMessage", "请检查标题、目的地、日期和备注内容。");
            return "redirect:/plans/" + id;
        }
        plan.setTitle(title.trim());
        plan.setDestination(destination.trim());
        plan.setStartDate(startDate);
        plan.setEndDate(endDate);
        plan.setBudget(budget);
        plan.setStatus(status);
        plan.setNotes(notes == null ? "" : notes.trim());
        planRepository.save(plan);
        redirectAttributes.addFlashAttribute("successMessage", "行程基础信息已更新。");
        return "redirect:/plans/" + id;
    }

    @PostMapping("/plans/{id}/activities")
    public String addActivity(
            @PathVariable Long id,
            @RequestParam LocalDate activityDate,
            @RequestParam(required = false) LocalTime startTime,
            @RequestParam String title,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String notes,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        TripPlan plan = planRepository.findById(id).orElseThrow();
        if (!accessService.canEdit(plan, currentUser)) return "redirect:/plans";
        if (title.isBlank() || title.length() > 100 || (location != null && location.length() > 100)
                || (notes != null && notes.length() > 500) || outsidePlan(plan, activityDate)) {
            redirectAttributes.addFlashAttribute("errorMessage", "请检查安排日期和内容；日期应位于计划范围内。");
            return "redirect:/plans/" + id + "#daily-itinerary";
        }
        TripPlanActivity activity = new TripPlanActivity();
        activity.setTripPlan(plan);
        activity.setCreatedBy(currentUser);
        activity.setActivityDate(activityDate);
        activity.setStartTime(startTime);
        activity.setTitle(title.trim());
        activity.setLocation(location == null ? "" : location.trim());
        activity.setNotes(notes == null ? "" : notes.trim());
        activityRepository.save(activity);
        return "redirect:/plans/" + id + "#daily-itinerary";
    }

    @PostMapping("/plans/{id}/suggestions/apply")
    public String applySuggestions(
            @PathVariable Long id,
            @RequestParam(required = false) List<String> suggestionKeys,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        TripPlan plan = planRepository.findById(id).orElseThrow();
        if (!accessService.canEdit(plan, currentUser)) return "redirect:/plans";
        List<TripPlanActivity> existing = activityRepository
                .findByTripPlanIdOrderByActivityDateAscStartTimeAscCreatedAtAsc(plan.getId());
        Map<String, SmartItineraryService.Suggestion> available = smartItineraryService.suggest(plan, existing).stream()
                .collect(java.util.stream.Collectors.toMap(SmartItineraryService.Suggestion::key, item -> item));
        Set<String> selected = suggestionKeys == null ? Set.of()
                : new java.util.LinkedHashSet<>(suggestionKeys.stream().limit(8).toList());
        List<TripPlanActivity> additions = selected.stream().map(available::get).filter(java.util.Objects::nonNull)
                .map(suggestion -> {
                    TripPlanActivity activity = new TripPlanActivity();
                    activity.setTripPlan(plan);
                    activity.setCreatedBy(currentUser);
                    activity.setActivityDate(suggestion.date());
                    activity.setStartTime(suggestion.time());
                    activity.setTitle(suggestion.title());
                    activity.setLocation(suggestion.location());
                    activity.setNotes(suggestion.note());
                    return activity;
                }).toList();
        if (additions.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "请选择仍然可用的行程建议。");
        } else {
            activityRepository.saveAll(additions);
            redirectAttributes.addFlashAttribute("successMessage", "已采用 " + additions.size() + " 条建议，原有安排保持不变。");
        }
        return "redirect:/plans/" + id + "#smart-itinerary";
    }

    @PostMapping("/plans/{planId}/activities/{activityId}/toggle")
    public String toggleActivity(@PathVariable Long planId, @PathVariable Long activityId, HttpSession session) {
        User user = currentUserService.getCurrentUser(session);
        TripPlanActivity activity = activityRepository.findById(activityId).orElseThrow();
        if (activity.getTripPlan().getId().equals(planId) && accessService.canEdit(activity.getTripPlan(), user)) {
            activity.setCompleted(!activity.isCompleted());
            activityRepository.save(activity);
        }
        return "redirect:/plans/" + planId + "#daily-itinerary";
    }

    @PostMapping("/plans/{planId}/activities/{activityId}/delete")
    public String deleteActivity(@PathVariable Long planId, @PathVariable Long activityId, HttpSession session) {
        User user = currentUserService.getCurrentUser(session);
        activityRepository.findById(activityId).filter(item -> item.getTripPlan().getId().equals(planId))
                .filter(item -> accessService.canEdit(item.getTripPlan(), user)).ifPresent(activityRepository::delete);
        return "redirect:/plans/" + planId + "#daily-itinerary";
    }

    @PostMapping("/plans/{id}/checklist")
    public String addChecklist(
            @PathVariable Long id,
            @RequestParam String title,
            @RequestParam TripChecklistCategory category,
            @RequestParam(required = false) Long assigneeId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        TripPlan plan = planRepository.findById(id).orElseThrow();
        if (!accessService.canEdit(plan, currentUser)) return "redirect:/plans";
        if (title.isBlank() || title.length() > 140
                || (assigneeId != null && !workspaceService.isParticipant(plan, assigneeId))) {
            redirectAttributes.addFlashAttribute("errorMessage", "准备事项内容或负责人无效。");
            return "redirect:/plans/" + id + "#trip-checklist";
        }
        TripChecklistItem item = new TripChecklistItem();
        item.setTripPlan(plan);
        item.setCreatedBy(currentUser);
        item.setTitle(title.trim());
        item.setCategory(category);
        if (assigneeId != null) {
            workspaceService.participants(plan).stream().filter(user -> user.getId().equals(assigneeId))
                    .findFirst().ifPresent(item::setAssignee);
        }
        checklistRepository.save(item);
        return "redirect:/plans/" + id + "#trip-checklist";
    }

    @PostMapping("/plans/{planId}/checklist/{itemId}/toggle")
    public String toggleChecklist(@PathVariable Long planId, @PathVariable Long itemId, HttpSession session) {
        User user = currentUserService.getCurrentUser(session);
        TripChecklistItem item = checklistRepository.findById(itemId).orElseThrow();
        if (item.getTripPlan().getId().equals(planId) && accessService.canEdit(item.getTripPlan(), user)) {
            item.setCompleted(!item.isCompleted());
            checklistRepository.save(item);
        }
        return "redirect:/plans/" + planId + "#trip-checklist";
    }

    @PostMapping("/plans/{planId}/checklist/{itemId}/delete")
    public String deleteChecklist(@PathVariable Long planId, @PathVariable Long itemId, HttpSession session) {
        User user = currentUserService.getCurrentUser(session);
        checklistRepository.findById(itemId).filter(item -> item.getTripPlan().getId().equals(planId))
                .filter(item -> accessService.canEdit(item.getTripPlan(), user)).ifPresent(checklistRepository::delete);
        return "redirect:/plans/" + planId + "#trip-checklist";
    }

    @PostMapping("/plans/{id}/share")
    public String toggleShare(
            @PathVariable Long id,
            @RequestParam boolean enabled,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = currentUserService.getCurrentUser(session);
        TripPlan plan = planRepository.findById(id).orElseThrow();
        if (!accessService.isOwner(plan, user)) return "redirect:/plans/" + id;
        if (enabled && (plan.getShareToken() == null || plan.getShareToken().isBlank())) {
            plan.setShareToken(UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", ""));
        }
        plan.setShareEnabled(enabled);
        planRepository.save(plan);
        redirectAttributes.addFlashAttribute("successMessage", enabled ? "只读分享链接已开启。" : "分享链接已关闭。");
        return "redirect:/plans/" + id + "#share-plan";
    }

    @GetMapping("/plans/{id}/calendar.ics")
    public ResponseEntity<byte[]> exportCalendar(@PathVariable Long id, HttpSession session) {
        User user = currentUserService.getCurrentUser(session);
        TripPlan plan = planRepository.findById(id).orElseThrow();
        if (!accessService.canView(plan, user)) return ResponseEntity.status(403).build();
        byte[] body = buildIcs(plan).getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("trip-plan-" + id + ".ics", StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(body);
    }

    @GetMapping("/shared/plans/{token}")
    public String sharedPlan(@PathVariable String token, Model model) {
        TripPlan plan = planRepository.findByShareTokenAndShareEnabledTrue(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<TripPlanActivity> activities = activityRepository
                .findByTripPlanIdOrderByActivityDateAscStartTimeAscCreatedAtAsc(plan.getId());
        model.addAttribute("plan", plan);
        model.addAttribute("activityGroups", groupActivities(activities));
        model.addAttribute("activityCount", activities.size());
        model.addAttribute("completedActivityCount", activities.stream().filter(TripPlanActivity::isCompleted).count());
        model.addAttribute("dayCount", tripDays(plan));
        return "shared-trip-plan";
    }

    private void addWorkspaceModel(TripPlan plan, User user, Model model) {
        List<TripPlanActivity> activities = activityRepository
                .findByTripPlanIdOrderByActivityDateAscStartTimeAscCreatedAtAsc(plan.getId());
        List<TripChecklistItem> checklist = checklistRepository
                .findByTripPlanIdOrderByCompletedAscCreatedAtAsc(plan.getId());
        var expenses = expenseRepository.findByTripPlanId(plan.getId());
        model.addAttribute("plan", plan);
        model.addAttribute("activityGroups", groupActivities(activities));
        model.addAttribute("activities", activities);
        model.addAttribute("checklist", checklist);
        model.addAttribute("checklistCategories", TripChecklistCategory.values());
        model.addAttribute("planStatuses", TripPlanStatus.values());
        model.addAttribute("participants", workspaceService.participants(plan));
        model.addAttribute("posts", postRepository.findByTripPlanIdOrderByTravelDateAscCreatedAtAsc(plan.getId()));
        model.addAttribute("expenses", expenses);
        model.addAttribute("spent", expenses.stream().map(expense -> expense.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("isOwner", accessService.isOwner(plan, user));
        model.addAttribute("activityCompleted", activities.stream().filter(TripPlanActivity::isCompleted).count());
        model.addAttribute("checklistCompleted", checklist.stream().filter(TripChecklistItem::isCompleted).count());
        model.addAttribute("checklistPercent", checklist.isEmpty() ? 0 : Math.round(checklist.stream().filter(TripChecklistItem::isCompleted).count() * 100.0 / checklist.size()));
        model.addAttribute("dayCount", tripDays(plan));
        model.addAttribute("defaultActivityDate", plan.getStartDate() == null ? LocalDate.now() : plan.getStartDate());
        model.addAttribute("smartSuggestions", smartItineraryService.suggest(plan, activities));
        model.addAttribute("readiness", readinessService.evaluate(plan, activities, checklist));
    }

    private Map<LocalDate, List<TripPlanActivity>> groupActivities(List<TripPlanActivity> activities) {
        Map<LocalDate, List<TripPlanActivity>> groups = new LinkedHashMap<>();
        activities.forEach(activity -> groups.computeIfAbsent(activity.getActivityDate(), key -> new java.util.ArrayList<>()).add(activity));
        return groups;
    }

    private boolean outsidePlan(TripPlan plan, LocalDate date) {
        return (plan.getStartDate() != null && date.isBefore(plan.getStartDate()))
                || (plan.getEndDate() != null && date.isAfter(plan.getEndDate()));
    }

    private long tripDays(TripPlan plan) {
        return plan.getStartDate() != null && plan.getEndDate() != null && !plan.getEndDate().isBefore(plan.getStartDate())
                ? ChronoUnit.DAYS.between(plan.getStartDate(), plan.getEndDate()) + 1 : 0;
    }

    private String buildIcs(TripPlan plan) {
        StringBuilder builder = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Travel Footprint//Trip Plan//ZH\r\nCALSCALE:GREGORIAN\r\n");
        List<TripPlanActivity> activities = activityRepository
                .findByTripPlanIdOrderByActivityDateAscStartTimeAscCreatedAtAsc(plan.getId());
        if (activities.isEmpty() && plan.getStartDate() != null) {
            appendAllDayEvent(builder, plan.getId() + "-plan", plan.getStartDate(),
                    plan.getEndDate() == null ? plan.getStartDate() : plan.getEndDate(), plan.getTitle(), plan.getDestination());
        } else {
            for (TripPlanActivity item : activities) {
                builder.append("BEGIN:VEVENT\r\nUID:").append(item.getId()).append("-activity@travelfootprint\r\n");
                if (item.getStartTime() == null) {
                    builder.append("DTSTART;VALUE=DATE:").append(item.getActivityDate().format(DateTimeFormatter.BASIC_ISO_DATE)).append("\r\n");
                } else {
                    builder.append("DTSTART:").append(item.getActivityDate().format(DateTimeFormatter.BASIC_ISO_DATE))
                            .append('T').append(item.getStartTime().format(DateTimeFormatter.ofPattern("HHmmss"))).append("\r\n");
                }
                builder.append("SUMMARY:").append(ics(item.getTitle())).append("\r\nLOCATION:").append(ics(item.getLocation()))
                        .append("\r\nDESCRIPTION:").append(ics(item.getNotes())).append("\r\nEND:VEVENT\r\n");
            }
        }
        return builder.append("END:VCALENDAR\r\n").toString();
    }

    private void appendAllDayEvent(StringBuilder builder, String uid, LocalDate start, LocalDate end,
            String summary, String location) {
        builder.append("BEGIN:VEVENT\r\nUID:").append(uid).append("@travelfootprint\r\nDTSTART;VALUE=DATE:")
                .append(start.format(DateTimeFormatter.BASIC_ISO_DATE)).append("\r\nDTEND;VALUE=DATE:")
                .append(end.plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE)).append("\r\nSUMMARY:")
                .append(ics(summary)).append("\r\nLOCATION:").append(ics(location)).append("\r\nEND:VEVENT\r\n");
    }

    private String ics(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\r", "").replace("\n", "\\n")
                .replace(",", "\\,").replace(";", "\\;");
    }
}

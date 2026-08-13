package com.example.travelfootprint.service;

import com.example.travelfootprint.model.NotificationType;
import com.example.travelfootprint.model.TripPlan;
import com.example.travelfootprint.model.TripPlanStatus;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.NotificationRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlanReminderService {

    private final TripPlanAccessService planAccessService;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public PlanReminderService(
            TripPlanAccessService planAccessService,
            NotificationRepository notificationRepository,
            NotificationService notificationService) {
        this.planAccessService = planAccessService;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.clock = Clock.systemDefaultZone();
    }

    public List<UpcomingPlan> syncAndList(User user) {
        LocalDate today = LocalDate.now(clock);
        return planAccessService.visiblePlans(user).stream()
                .filter(plan -> plan.getStatus() != TripPlanStatus.FINISHED)
                .filter(plan -> plan.getStartDate() != null)
                .map(plan -> new UpcomingPlan(plan, ChronoUnit.DAYS.between(today, plan.getStartDate())))
                .filter(item -> item.daysUntilStart() >= 0 && item.daysUntilStart() <= 7)
                .peek(item -> createReminderOnce(user, item))
                .toList();
    }

    private void createReminderOnce(User user, UpcomingPlan item) {
        String link = "/plans#plan-" + item.plan().getId();
        if (notificationRepository.existsByRecipientIdAndTypeAndLinkPath(
                user.getId(), NotificationType.PLAN_REMINDER, link)) {
            return;
        }
        String timing = item.daysUntilStart() == 0 ? "今天出发" : item.daysUntilStart() + " 天后出发";
        notificationService.notify(
                user,
                null,
                NotificationType.PLAN_REMINDER,
                "行程提醒：" + item.plan().getTitle() + "将在" + timing,
                link);
    }

    public record UpcomingPlan(TripPlan plan, long daysUntilStart) {
    }
}

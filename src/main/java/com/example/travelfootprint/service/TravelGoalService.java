package com.example.travelfootprint.service;

import com.example.travelfootprint.model.TravelGoal;
import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.TripPlanStatus;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.TravelGoalRepository;
import com.example.travelfootprint.repository.TravelPostRepository;
import com.example.travelfootprint.repository.TripPlanRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TravelGoalService {
    private final TravelGoalRepository goalRepository;
    private final TravelPostRepository postRepository;
    private final TripPlanRepository planRepository;
    private final ProvinceCatalogService provinceCatalogService;

    public TravelGoalService(TravelGoalRepository goalRepository, TravelPostRepository postRepository,
                             TripPlanRepository planRepository, ProvinceCatalogService provinceCatalogService) {
        this.goalRepository = goalRepository;
        this.postRepository = postRepository;
        this.planRepository = planRepository;
        this.provinceCatalogService = provinceCatalogService;
    }

    public List<GoalView> goals(User user) {
        List<TravelPost> posts = postRepository.findByAuthorIdOrderByCreatedAtDesc(user.getId());
        return goalRepository.findByOwnerIdOrderByTargetYearDescCreatedAtDesc(user.getId()).stream()
                .map(goal -> view(goal, posts, user)).toList();
    }

    private GoalView view(TravelGoal goal, List<TravelPost> allPosts, User user) {
        List<TravelPost> posts = allPosts.stream().filter(post -> post.getTravelDate() != null
                && post.getTravelDate().getYear() == goal.getTargetYear()).toList();
        long current = switch (goal.getType()) {
            case FOOTPRINTS -> posts.size();
            case PROVINCES -> posts.stream().map(post -> provinceCatalogService
                    .resolveProvince(post.getProvince(), post.getLocation()).orElse(""))
                    .filter(value -> !value.isBlank()).distinct().count();
            case TRAVEL_DAYS -> posts.stream().map(TravelPost::getTravelDate).distinct().count();
            case TRIPS -> planRepository.findByOwnerIdOrderByStartDateAscCreatedAtDesc(user.getId()).stream()
                    .filter(plan -> plan.getStatus() == TripPlanStatus.FINISHED)
                    .filter(plan -> plan.getEndDate() != null && plan.getEndDate().getYear() == goal.getTargetYear()).count();
        };
        long percent = Math.min(100, Math.round(current * 100.0 / goal.getTargetValue()));
        long remaining = Math.max(0, goal.getTargetValue() - current);
        return new GoalView(goal, current, remaining, percent, current >= goal.getTargetValue());
    }

    public record GoalView(TravelGoal goal, long current, long remaining, long percent, boolean completed) { }
}

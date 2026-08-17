package com.example.travelfootprint.service;

import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.TravelExpenseRepository;
import com.example.travelfootprint.repository.TravelGoalRepository;
import com.example.travelfootprint.repository.TravelPostPhotoRepository;
import com.example.travelfootprint.repository.TravelPostRepository;
import com.example.travelfootprint.repository.TripPlanRepository;
import com.example.travelfootprint.repository.DestinationWishRepository;
import com.example.travelfootprint.repository.TripPlanActivityRepository;
import com.example.travelfootprint.repository.TripChecklistItemRepository;
import com.example.travelfootprint.repository.RecommendationDismissalRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PersonalDataExportService {
    private final ObjectMapper objectMapper;
    private final TravelPostRepository postRepository;
    private final TravelPostPhotoRepository photoRepository;
    private final TripPlanRepository planRepository;
    private final TravelExpenseRepository expenseRepository;
    private final TravelGoalRepository goalRepository;
    private final DestinationWishRepository wishRepository;
    private final TripPlanActivityRepository activityRepository;
    private final TripChecklistItemRepository checklistRepository;
    private final RecommendationDismissalRepository recommendationDismissalRepository;

    public PersonalDataExportService(ObjectMapper objectMapper, TravelPostRepository postRepository,
            TravelPostPhotoRepository photoRepository, TripPlanRepository planRepository,
            TravelExpenseRepository expenseRepository, TravelGoalRepository goalRepository,
            DestinationWishRepository wishRepository, TripPlanActivityRepository activityRepository,
            TripChecklistItemRepository checklistRepository,
            RecommendationDismissalRepository recommendationDismissalRepository) {
        this.objectMapper = objectMapper; this.postRepository = postRepository; this.photoRepository = photoRepository;
        this.planRepository = planRepository; this.expenseRepository = expenseRepository; this.goalRepository = goalRepository;
        this.wishRepository = wishRepository; this.activityRepository = activityRepository;
        this.checklistRepository = checklistRepository;
        this.recommendationDismissalRepository = recommendationDismissalRepository;
    }

    public byte[] export(User user) throws JsonProcessingException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("exportedAt", java.time.LocalDateTime.now().toString());
        root.put("profile", map("username", user.getUsername(), "nickname", user.getNickname(), "bio", user.getBio(),
                "avatarPath", user.getAvatarPath(), "joinedAt", String.valueOf(user.getJoinedAt())));
        var posts = postRepository.findByAuthorIdOrderByCreatedAtDesc(user.getId());
        root.put("footprints", posts.stream().map(post -> {
            Map<String, Object> item = map("id", post.getId(), "title", post.getTitle(), "location", post.getLocation(),
                    "province", post.getProvince(), "content", post.getContent(), "travelDate", post.getTravelDate(),
                    "category", post.getCategory(), "tags", post.getTags(), "visibility", post.getVisibility(),
                    "approximateLocation", post.isApproximateLocation(), "coverPhotoPath", post.getPhotoPath(),
                    "videoPath", post.getVideoPath(),
                    "createdAt", post.getCreatedAt());
            item.put("photos", photoRepository.findByPostIdOrderBySortOrderAscIdAsc(post.getId()).stream()
                    .map(photo -> map("path", photo.getPhotoPath(), "sortOrder", photo.getSortOrder(), "cover", photo.isCover())).toList());
            return item;
        }).toList());
        root.put("tripPlans", planRepository.findByOwnerIdOrderByStartDateAscCreatedAtDesc(user.getId()).stream()
                .map(plan -> map("id", plan.getId(), "title", plan.getTitle(), "destination", plan.getDestination(),
                        "startDate", plan.getStartDate(), "endDate", plan.getEndDate(), "budget", plan.getBudget(),
                        "status", plan.getStatus(), "notes", plan.getNotes())).toList());
        root.put("expenses", expenseRepository.findByOwnerIdAndOccurredOnBetweenOrderByOccurredOnDescCreatedAtDesc(
                        user.getId(), LocalDate.of(2000, 1, 1), LocalDate.of(2999, 12, 31)).stream()
                .map(expense -> map("id", expense.getId(), "amount", expense.getAmount(), "category", expense.getCategory(),
                        "occurredOn", expense.getOccurredOn(), "note", expense.getNote())).toList());
        root.put("goals", goalRepository.findByOwnerIdOrderByTargetYearDescCreatedAtDesc(user.getId()).stream()
                .map(goal -> map("id", goal.getId(), "title", goal.getTitle(), "type", goal.getType(),
                        "targetValue", goal.getTargetValue(), "targetYear", goal.getTargetYear())).toList());
        root.put("destinationWishlist", wishRepository.findByOwnerIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(wish -> map("id", wish.getId(), "destination", wish.getDestination(), "province", wish.getProvince(),
                        "note", wish.getNote(), "priority", wish.getPriority(), "status", wish.getStatus(),
                        "targetYear", wish.getTargetYear(), "tripPlanId", wish.getTripPlan() == null ? null : wish.getTripPlan().getId())).toList());
        var ownedPlans = planRepository.findByOwnerIdOrderByStartDateAscCreatedAtDesc(user.getId());
        root.put("tripActivities", ownedPlans.stream().flatMap(plan -> activityRepository
                        .findByTripPlanIdOrderByActivityDateAscStartTimeAscCreatedAtAsc(plan.getId()).stream())
                .map(activity -> map("id", activity.getId(), "tripPlanId", activity.getTripPlan().getId(),
                        "date", activity.getActivityDate(), "time", activity.getStartTime(), "title", activity.getTitle(),
                        "location", activity.getLocation(), "notes", activity.getNotes(), "completed", activity.isCompleted())).toList());
        root.put("tripChecklist", ownedPlans.stream().flatMap(plan -> checklistRepository
                        .findByTripPlanIdOrderByCompletedAscCreatedAtAsc(plan.getId()).stream())
                .map(item -> map("id", item.getId(), "tripPlanId", item.getTripPlan().getId(), "title", item.getTitle(),
                        "category", item.getCategory(), "completed", item.isCompleted(),
                        "assignee", item.getAssignee() == null ? null : item.getAssignee().getUsername())).toList());
        root.put("dismissedRecommendations", recommendationDismissalRepository.findByUserId(user.getId()).stream()
                .map(item -> map("postId", item.getPost().getId(), "postTitle", item.getPost().getTitle(),
                        "dismissedAt", item.getCreatedAt())).toList());
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
    }

    private Map<String, Object> map(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < entries.length; index += 2) result.put(String.valueOf(entries[index]), entries[index + 1]);
        return result;
    }
}

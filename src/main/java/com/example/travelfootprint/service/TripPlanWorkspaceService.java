package com.example.travelfootprint.service;

import com.example.travelfootprint.model.DestinationWishStatus;
import com.example.travelfootprint.model.TripPlan;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.DestinationWishRepository;
import com.example.travelfootprint.repository.TripChecklistItemRepository;
import com.example.travelfootprint.repository.TripPlanActivityRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TripPlanWorkspaceService {

    private final TripPlanAccessService accessService;
    private final TripPlanActivityRepository activityRepository;
    private final TripChecklistItemRepository checklistRepository;
    private final DestinationWishRepository wishRepository;

    public TripPlanWorkspaceService(
            TripPlanAccessService accessService,
            TripPlanActivityRepository activityRepository,
            TripChecklistItemRepository checklistRepository,
            DestinationWishRepository wishRepository) {
        this.accessService = accessService;
        this.activityRepository = activityRepository;
        this.checklistRepository = checklistRepository;
        this.wishRepository = wishRepository;
    }

    public List<User> participants(TripPlan plan) {
        List<User> users = new ArrayList<>();
        users.add(plan.getOwner());
        accessService.acceptedMembers(plan).stream().map(member -> member.getUser()).forEach(users::add);
        return users;
    }

    public boolean isParticipant(TripPlan plan, Long userId) {
        return userId != null && participants(plan).stream().anyMatch(user -> user.getId().equals(userId));
    }

    public void cleanupBeforePlanDeletion(TripPlan plan) {
        activityRepository.deleteByTripPlanId(plan.getId());
        checklistRepository.deleteByTripPlanId(plan.getId());
        var wishes = wishRepository.findByTripPlanId(plan.getId());
        wishes.forEach(wish -> {
            wish.setTripPlan(null);
            if (wish.getStatus() == DestinationWishStatus.PLANNED) {
                wish.setStatus(DestinationWishStatus.WISH);
            }
        });
        wishRepository.saveAll(wishes);
    }
}

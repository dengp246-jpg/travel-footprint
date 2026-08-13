package com.example.travelfootprint.service;

import com.example.travelfootprint.model.TripPlan;
import com.example.travelfootprint.model.TripPlanMember;
import com.example.travelfootprint.model.TripPlanMemberStatus;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.TripPlanMemberRepository;
import com.example.travelfootprint.repository.TripPlanRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TripPlanAccessService {

    private final TripPlanRepository planRepository;
    private final TripPlanMemberRepository memberRepository;

    public TripPlanAccessService(TripPlanRepository planRepository, TripPlanMemberRepository memberRepository) {
        this.planRepository = planRepository;
        this.memberRepository = memberRepository;
    }

    public List<TripPlan> visiblePlans(User user) {
        if (user == null) {
            return List.of();
        }
        List<TripPlan> plans = new ArrayList<>(
                planRepository.findByOwnerIdOrderByStartDateAscCreatedAtDesc(user.getId()));
        memberRepository.findByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), TripPlanMemberStatus.ACCEPTED)
                .stream().map(TripPlanMember::getTripPlan)
                .filter(plan -> plans.stream().noneMatch(item -> item.getId().equals(plan.getId())))
                .forEach(plans::add);
        plans.sort(Comparator.comparing(
                        TripPlan::getStartDate,
                        Comparator.nullsLast(LocalDate::compareTo))
                .thenComparing(TripPlan::getCreatedAt, Comparator.reverseOrder()));
        return plans;
    }

    public boolean canView(TripPlan plan, User user) {
        return plan != null && user != null && (isOwner(plan, user)
                || memberRepository.existsByTripPlanIdAndUserIdAndStatus(
                        plan.getId(), user.getId(), TripPlanMemberStatus.ACCEPTED));
    }

    public boolean canEdit(TripPlan plan, User user) {
        return canView(plan, user);
    }

    public boolean isOwner(TripPlan plan, User user) {
        return plan != null && user != null && plan.getOwner().getId().equals(user.getId());
    }

    public List<TripPlanMember> acceptedMembers(TripPlan plan) {
        return memberRepository.findByTripPlanIdAndStatusOrderByCreatedAtAsc(
                plan.getId(), TripPlanMemberStatus.ACCEPTED);
    }

    public List<TripPlanMember> allInvitations(TripPlan plan) {
        return memberRepository.findByTripPlanIdOrderByCreatedAtAsc(plan.getId());
    }

    public List<TripPlanMember> pendingInvitations(User user) {
        return memberRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                user.getId(), TripPlanMemberStatus.PENDING);
    }
}

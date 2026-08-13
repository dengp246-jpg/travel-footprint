package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.TripPlanMember;
import com.example.travelfootprint.model.TripPlanMemberStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface TripPlanMemberRepository extends JpaRepository<TripPlanMember, Long> {

    Optional<TripPlanMember> findByTripPlanIdAndUserId(Long tripPlanId, Long userId);

    List<TripPlanMember> findByTripPlanIdOrderByCreatedAtAsc(Long tripPlanId);

    List<TripPlanMember> findByTripPlanIdAndStatusOrderByCreatedAtAsc(Long tripPlanId, TripPlanMemberStatus status);

    List<TripPlanMember> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, TripPlanMemberStatus status);

    boolean existsByTripPlanIdAndUserIdAndStatus(Long tripPlanId, Long userId, TripPlanMemberStatus status);

    @Transactional
    void deleteByTripPlanId(Long tripPlanId);
}

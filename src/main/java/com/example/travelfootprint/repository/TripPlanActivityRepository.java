package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.TripPlanActivity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface TripPlanActivityRepository extends JpaRepository<TripPlanActivity, Long> {
    List<TripPlanActivity> findByTripPlanIdOrderByActivityDateAscStartTimeAscCreatedAtAsc(Long tripPlanId);
    @Transactional
    void deleteByTripPlanId(Long tripPlanId);
}

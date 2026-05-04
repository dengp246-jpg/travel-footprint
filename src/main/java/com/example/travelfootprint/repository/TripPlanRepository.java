package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.TripPlan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripPlanRepository extends JpaRepository<TripPlan, Long> {

    List<TripPlan> findByOwnerIdOrderByStartDateAscCreatedAtDesc(Long ownerId);
}

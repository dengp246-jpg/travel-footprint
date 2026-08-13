package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.TravelGoal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelGoalRepository extends JpaRepository<TravelGoal, Long> {
    List<TravelGoal> findByOwnerIdOrderByTargetYearDescCreatedAtDesc(Long ownerId);
}

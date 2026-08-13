package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.TravelExpense;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelExpenseRepository extends JpaRepository<TravelExpense, Long> {

    List<TravelExpense> findByOwnerIdOrderByOccurredOnDescCreatedAtDesc(Long ownerId);

    List<TravelExpense> findByOwnerIdAndOccurredOnBetweenOrderByOccurredOnDescCreatedAtDesc(
            Long ownerId, LocalDate startDate, LocalDate endDate);

    List<TravelExpense> findByTripPlanId(Long tripPlanId);

    List<TravelExpense> findByTravelPostId(Long travelPostId);
}

package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.TravelExpense;
import java.util.Collection;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelExpenseRepository extends JpaRepository<TravelExpense, Long> {

    List<TravelExpense> findByOwnerIdOrderByOccurredOnDescCreatedAtDesc(Long ownerId);

    List<TravelExpense> findByOwnerIdAndOccurredOnBetweenOrderByOccurredOnDescCreatedAtDesc(
            Long ownerId, LocalDate startDate, LocalDate endDate);

    List<TravelExpense> findByTripPlanId(Long tripPlanId);

    List<TravelExpense> findByTravelPostId(Long travelPostId);

    @Query("select expense.tripPlan.id as planId, sum(expense.amount) as total "
            + "from TravelExpense expense where expense.tripPlan.id in :planIds group by expense.tripPlan.id")
    List<PlanExpenseTotal> sumByTripPlanIds(@Param("planIds") Collection<Long> planIds);

    interface PlanExpenseTotal {
        Long getPlanId();

        java.math.BigDecimal getTotal();
    }
}

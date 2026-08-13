package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.TripChecklistItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface TripChecklistItemRepository extends JpaRepository<TripChecklistItem, Long> {
    List<TripChecklistItem> findByTripPlanIdOrderByCompletedAscCreatedAtAsc(Long tripPlanId);
    @Transactional
    void deleteByTripPlanId(Long tripPlanId);
}

package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.DestinationWish;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DestinationWishRepository extends JpaRepository<DestinationWish, Long> {
    List<DestinationWish> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    List<DestinationWish> findByTripPlanId(Long tripPlanId);
}

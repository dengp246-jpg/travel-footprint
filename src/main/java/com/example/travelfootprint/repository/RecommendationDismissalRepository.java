package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.RecommendationDismissal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationDismissalRepository extends JpaRepository<RecommendationDismissal, Long> {
    List<RecommendationDismissal> findByUserId(Long userId);
    boolean existsByUserIdAndPostId(Long userId, Long postId);
    void deleteByUserId(Long userId);
    void deleteByPostId(Long postId);
}

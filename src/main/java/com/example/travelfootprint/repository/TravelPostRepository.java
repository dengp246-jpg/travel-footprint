package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.TravelPost;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelPostRepository extends JpaRepository<TravelPost, Long> {

    List<TravelPost> findAllByOrderByCreatedAtDesc();

    List<TravelPost> findByAuthorIdOrderByCreatedAtDesc(Long authorId);

    boolean existsBySourceUrl(String sourceUrl);

    Optional<TravelPost> findBySourceUrl(String sourceUrl);
}

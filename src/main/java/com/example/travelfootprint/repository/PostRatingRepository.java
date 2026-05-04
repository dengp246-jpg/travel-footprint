package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.PostRating;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRatingRepository extends JpaRepository<PostRating, Long> {

    Optional<PostRating> findByPostIdAndUserId(Long postId, Long userId);

    List<PostRating> findByPostId(Long postId);

    long countByPostId(Long postId);

    void deleteByPostId(Long postId);
}

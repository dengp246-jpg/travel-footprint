package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.PostFavorite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostFavoriteRepository extends JpaRepository<PostFavorite, Long> {

    Optional<PostFavorite> findByPostIdAndUserId(Long postId, Long userId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    List<PostFavorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByPostId(Long postId);

    void deleteByPostId(Long postId);
}

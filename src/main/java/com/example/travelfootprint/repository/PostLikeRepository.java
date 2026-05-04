package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.PostLike;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId);

    List<PostLike> findByUserId(Long userId);

    long countByPostId(Long postId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    void deleteByPostId(Long postId);
}

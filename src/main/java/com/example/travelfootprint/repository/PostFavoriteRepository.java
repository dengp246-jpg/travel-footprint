package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.PostFavorite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostFavoriteRepository extends JpaRepository<PostFavorite, Long> {

    Optional<PostFavorite> findByPostIdAndUserId(Long postId, Long userId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    List<PostFavorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByPostId(Long postId);

    void deleteByPostId(Long postId);

    @Query("select item.post.id, count(item) from PostFavorite item where item.post.id in :postIds group by item.post.id")
    List<Object[]> countGroupedByPostIds(@Param("postIds") List<Long> postIds);
}

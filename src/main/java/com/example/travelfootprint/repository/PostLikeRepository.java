package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.PostLike;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId);

    List<PostLike> findByUserId(Long userId);

    long countByPostId(Long postId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    void deleteByPostId(Long postId);

    @Query("select item.post.id, count(item) from PostLike item where item.post.id in :postIds group by item.post.id")
    List<Object[]> countGroupedByPostIds(@Param("postIds") List<Long> postIds);
}

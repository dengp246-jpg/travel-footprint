package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.PostRating;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRatingRepository extends JpaRepository<PostRating, Long> {

    Optional<PostRating> findByPostIdAndUserId(Long postId, Long userId);

    List<PostRating> findByPostId(Long postId);

    long countByPostId(Long postId);

    void deleteByPostId(Long postId);

    List<PostRating> findByPostIdInAndUserId(List<Long> postIds, Long userId);

    @Query("select item.post.id, avg(item.score) from PostRating item where item.post.id in :postIds group by item.post.id")
    List<Object[]> averageGroupedByPostIds(@Param("postIds") List<Long> postIds);
}

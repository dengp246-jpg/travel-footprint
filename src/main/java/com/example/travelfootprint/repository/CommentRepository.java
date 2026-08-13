package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.Comment;
import com.example.travelfootprint.model.ContentReviewStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId);

    List<Comment> findByPostIdAndParentCommentIsNullOrderByCreatedAtAsc(Long postId);

    List<Comment> findByParentCommentIdOrderByCreatedAtAsc(Long parentCommentId);

    long countByPostId(Long postId);

    void deleteByPostId(Long postId);

    @Query("""
            select item.post.id, count(item)
            from Comment item
            where item.post.id in :postIds
              and (item.reviewStatus = :approvedStatus or item.reviewStatus is null)
              and (item.author.enabled = true or item.author.enabled is null)
            group by item.post.id
            """)
    List<Object[]> countVisibleGroupedByPostIds(
            @Param("postIds") List<Long> postIds,
            @Param("approvedStatus") ContentReviewStatus approvedStatus);
}

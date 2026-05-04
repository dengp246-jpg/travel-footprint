package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.Comment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId);

    List<Comment> findByPostIdAndParentCommentIsNullOrderByCreatedAtAsc(Long postId);

    List<Comment> findByParentCommentIdOrderByCreatedAtAsc(Long parentCommentId);

    long countByPostId(Long postId);

    void deleteByPostId(Long postId);
}

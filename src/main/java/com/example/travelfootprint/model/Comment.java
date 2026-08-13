package com.example.travelfootprint.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "comment_entry", indexes = {
        @Index(name = "idx_comment_post_review", columnList = "post_id, review_status"),
        @Index(name = "idx_comment_parent_created", columnList = "parent_comment_id, created_at")
})
public class Comment extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private TravelPost post;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private User author;

    @ManyToOne(fetch = FetchType.EAGER)
    private Comment parentComment;

    @Column(nullable = false, length = 1000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ContentReviewStatus reviewStatus;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public TravelPost getPost() {
        return post;
    }

    public void setPost(TravelPost post) {
        this.post = post;
    }

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public ContentReviewStatus getReviewStatus() {
        return reviewStatus == null ? ContentReviewStatus.APPROVED : reviewStatus;
    }

    public void setReviewStatus(ContentReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public Comment getParentComment() {
        return parentComment;
    }

    public void setParentComment(Comment parentComment) {
        this.parentComment = parentComment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

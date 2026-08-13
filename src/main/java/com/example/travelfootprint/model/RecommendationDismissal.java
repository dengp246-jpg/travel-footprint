package com.example.travelfootprint.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(name = "recommendation_dismissal", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "post_id"})
})
public class RecommendationDismissal extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private TravelPost post;

    private LocalDateTime createdAt = LocalDateTime.now();

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public TravelPost getPost() { return post; }
    public void setPost(TravelPost post) { this.post = post; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

package com.example.travelfootprint.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(name = "post_favorite", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"post_id", "user_id"})
})
public class PostFavorite extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private TravelPost post;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private User user;

    private LocalDateTime createdAt = LocalDateTime.now();

    public TravelPost getPost() {
        return post;
    }

    public void setPost(TravelPost post) {
        this.post = post;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

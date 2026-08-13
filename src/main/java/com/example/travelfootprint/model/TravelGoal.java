package com.example.travelfootprint.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "travel_goal")
public class TravelGoal extends BaseEntity {
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private User owner;
    @Column(nullable = false, length = 100)
    private String title;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TravelGoalType type;
    @Column(nullable = false)
    private int targetValue;
    @Column(nullable = false)
    private int targetYear;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist public void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public TravelGoalType getType() { return type; }
    public void setType(TravelGoalType type) { this.type = type; }
    public int getTargetValue() { return targetValue; }
    public void setTargetValue(int targetValue) { this.targetValue = targetValue; }
    public int getTargetYear() { return targetYear; }
    public void setTargetYear(int targetYear) { this.targetYear = targetYear; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

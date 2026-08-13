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
@Table(name = "trip_checklist_item")
public class TripChecklistItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private TripPlan tripPlan;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.EAGER)
    private User assignee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripChecklistCategory category = TripChecklistCategory.OTHER;

    @Column(nullable = false, length = 140)
    private String title;

    private Boolean completed;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public TripPlan getTripPlan() { return tripPlan; }
    public void setTripPlan(TripPlan tripPlan) { this.tripPlan = tripPlan; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public User getAssignee() { return assignee; }
    public void setAssignee(User assignee) { this.assignee = assignee; }
    public TripChecklistCategory getCategory() { return category; }
    public void setCategory(TripChecklistCategory category) { this.category = category; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public boolean isCompleted() { return Boolean.TRUE.equals(completed); }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

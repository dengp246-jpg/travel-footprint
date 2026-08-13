package com.example.travelfootprint.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "trip_plan_activity")
public class TripPlanActivity extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private TripPlan tripPlan;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private User createdBy;

    @Column(nullable = false)
    private LocalDate activityDate;

    private LocalTime startTime;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 100)
    private String location;

    @Column(length = 500)
    private String notes;

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
    public LocalDate getActivityDate() { return activityDate; }
    public void setActivityDate(LocalDate activityDate) { this.activityDate = activityDate; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isCompleted() { return Boolean.TRUE.equals(completed); }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

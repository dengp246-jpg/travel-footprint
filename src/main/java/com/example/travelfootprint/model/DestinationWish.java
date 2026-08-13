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
@Table(name = "destination_wish")
public class DestinationWish extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private User owner;

    @Column(nullable = false, length = 100)
    private String destination;

    @Column(length = 40)
    private String province;

    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DestinationWishPriority priority = DestinationWishPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DestinationWishStatus status = DestinationWishStatus.WISH;

    private Integer targetYear;

    @ManyToOne(fetch = FetchType.EAGER)
    private TripPlan tripPlan;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public DestinationWishPriority getPriority() { return priority; }
    public void setPriority(DestinationWishPriority priority) { this.priority = priority; }
    public DestinationWishStatus getStatus() { return status; }
    public void setStatus(DestinationWishStatus status) { this.status = status; }
    public Integer getTargetYear() { return targetYear; }
    public void setTargetYear(Integer targetYear) { this.targetYear = targetYear; }
    public TripPlan getTripPlan() { return tripPlan; }
    public void setTripPlan(TripPlan tripPlan) { this.tripPlan = tripPlan; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "travel_expense", indexes = {
        @Index(name = "idx_expense_owner_date", columnList = "owner_id, occurred_on")
})
public class TravelExpense extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private User owner;

    @ManyToOne(fetch = FetchType.EAGER)
    private TripPlan tripPlan;

    @ManyToOne(fetch = FetchType.EAGER)
    private TravelPost travelPost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ExpenseCategory category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate occurredOn;

    @Column(length = 240)
    private String note;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public TripPlan getTripPlan() { return tripPlan; }
    public void setTripPlan(TripPlan tripPlan) { this.tripPlan = tripPlan; }
    public TravelPost getTravelPost() { return travelPost; }
    public void setTravelPost(TravelPost travelPost) { this.travelPost = travelPost; }
    public ExpenseCategory getCategory() { return category; }
    public void setCategory(ExpenseCategory category) { this.category = category; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getOccurredOn() { return occurredOn; }
    public void setOccurredOn(LocalDate occurredOn) { this.occurredOn = occurredOn; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

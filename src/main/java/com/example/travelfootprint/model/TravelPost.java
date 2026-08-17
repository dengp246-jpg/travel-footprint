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
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "travel_post", indexes = {
        @Index(name = "idx_travel_post_review_created", columnList = "review_status, created_at"),
        @Index(name = "idx_travel_post_author_created", columnList = "author_id, created_at"),
        @Index(name = "idx_travel_post_province", columnList = "province")
})
public class TravelPost extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private User author;

    @ManyToOne(fetch = FetchType.EAGER)
    private TripPlan tripPlan;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 100)
    private String location;

    @Column(length = 20)
    private String province;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(length = 255)
    private String photoPath;

    @Column(length = 255)
    private String videoPath;

    @Column(length = 255)
    private String sourceName;

    @Column(length = 500, unique = true)
    private String sourceUrl;

    @Column(length = 100)
    private String tags;

    @Column(length = 40)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ContentReviewStatus reviewStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PostVisibility visibility;

    private Boolean approximateLocation;

    private Double latitude;

    private Double longitude;

    @Column(nullable = false)
    private LocalDate travelDate;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }

    public TripPlan getTripPlan() {
        return tripPlan;
    }

    public void setTripPlan(TripPlan tripPlan) {
        this.tripPlan = tripPlan;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getContent() {
        return content;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
    }

    public String getVideoPath() {
        return videoPath;
    }

    public void setVideoPath(String videoPath) {
        this.videoPath = videoPath;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public ContentReviewStatus getReviewStatus() {
        return reviewStatus == null ? ContentReviewStatus.APPROVED : reviewStatus;
    }

    public void setReviewStatus(ContentReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public PostVisibility getVisibility() {
        return visibility == null ? PostVisibility.PUBLIC : visibility;
    }

    public void setVisibility(PostVisibility visibility) {
        this.visibility = visibility;
    }

    public boolean isApproximateLocation() {
        return Boolean.TRUE.equals(approximateLocation);
    }

    public void setApproximateLocation(boolean approximateLocation) {
        this.approximateLocation = approximateLocation;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public LocalDate getTravelDate() {
        return travelDate;
    }

    public void setTravelDate(LocalDate travelDate) {
        this.travelDate = travelDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

package com.example.travelfootprint.model;

public enum ContentReviewStatus {
    APPROVED("已通过"),
    PENDING("待审核"),
    REJECTED("未通过");

    private final String label;

    ContentReviewStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

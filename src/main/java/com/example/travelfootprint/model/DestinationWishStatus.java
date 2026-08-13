package com.example.travelfootprint.model;

public enum DestinationWishStatus {
    WISH("想去"),
    PLANNED("已规划"),
    VISITED("已抵达");

    private final String label;

    DestinationWishStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

package com.example.travelfootprint.model;

public enum DestinationWishPriority {
    HIGH("优先想去"),
    MEDIUM("近期考虑"),
    LOW("以后探索");

    private final String label;

    DestinationWishPriority(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

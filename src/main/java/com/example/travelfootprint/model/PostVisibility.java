package com.example.travelfootprint.model;

public enum PostVisibility {
    PUBLIC("公开"),
    FOLLOWERS("仅关注者"),
    PRIVATE("仅自己");

    private final String label;

    PostVisibility(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

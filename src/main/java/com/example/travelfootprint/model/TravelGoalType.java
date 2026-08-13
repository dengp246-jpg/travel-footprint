package com.example.travelfootprint.model;

public enum TravelGoalType {
    FOOTPRINTS("发布足迹"),
    PROVINCES("点亮省份"),
    TRAVEL_DAYS("累计旅行日"),
    TRIPS("完成行程");

    private final String label;

    TravelGoalType(String label) { this.label = label; }

    public String getLabel() { return label; }
}

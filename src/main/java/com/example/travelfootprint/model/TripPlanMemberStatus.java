package com.example.travelfootprint.model;

public enum TripPlanMemberStatus {
    PENDING("待确认"),
    ACCEPTED("已加入"),
    DECLINED("已拒绝");

    private final String label;

    TripPlanMemberStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

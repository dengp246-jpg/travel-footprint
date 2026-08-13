package com.example.travelfootprint.model;

public enum TripChecklistCategory {
    DOCUMENTS("证件票务"),
    TRANSPORT("交通出行"),
    STAY("住宿预订"),
    PACKING("行李准备"),
    OTHER("其他事项");

    private final String label;

    TripChecklistCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

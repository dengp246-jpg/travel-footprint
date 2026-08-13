package com.example.travelfootprint.model;

public enum ExpenseCategory {
    TRANSPORT("交通"),
    ACCOMMODATION("住宿"),
    FOOD("餐饮"),
    TICKET("门票"),
    SHOPPING("购物"),
    OTHER("其他");

    private final String label;

    ExpenseCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

package com.example.travelfootprint.model;

public enum NotificationType {
    LIKE("点赞"),
    COMMENT("评论"),
    REPLY("回复"),
    FOLLOW("关注"),
    MESSAGE("私信"),
    FAVORITE("收藏"),
    RATING("评分"),
    PLAN_INVITE("同行邀请"),
    PLAN_REMINDER("行程提醒");

    private final String label;

    NotificationType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

package com.example.travelfootprint.service;

import com.example.travelfootprint.model.TripChecklistCategory;
import com.example.travelfootprint.model.TripChecklistItem;
import com.example.travelfootprint.model.TripPlan;
import com.example.travelfootprint.model.TripPlanActivity;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TripReadinessService {

    public Readiness evaluate(TripPlan plan, List<TripPlanActivity> activities, List<TripChecklistItem> checklist) {
        int score = 0;
        List<String> improvements = new ArrayList<>();
        if (plan.getStartDate() != null && plan.getEndDate() != null) score += 15;
        else improvements.add("补全出发与返程日期");
        if (!activities.isEmpty()) score += 20;
        else improvements.add("至少添加一项每日行程");
        if (!checklist.isEmpty()) score += 10;
        else improvements.add("建立出发准备清单");
        long completed = checklist.stream().filter(TripChecklistItem::isCompleted).count();
        if (!checklist.isEmpty()) score += (int) Math.round(completed * 25.0 / checklist.size());
        if (!checklist.isEmpty() && completed < checklist.size()) improvements.add("继续完成未勾选的准备事项");
        if (plan.getBudget() != null && plan.getBudget().signum() > 0) score += 10;
        else improvements.add("设置旅行预算");
        score += categoryScore(checklist, TripChecklistCategory.DOCUMENTS, "补充证件或票务事项", improvements);
        score += categoryScore(checklist, TripChecklistCategory.TRANSPORT, "确认交通安排", improvements);
        score += categoryScore(checklist, TripChecklistCategory.STAY, "确认住宿安排", improvements);
        if (plan.getNotes() != null && !plan.getNotes().isBlank()) score += 5;
        else improvements.add("补充集合方式或旅行说明");

        String level = score >= 85 ? "整装待发" : score >= 65 ? "准备充分" : score >= 40 ? "继续完善" : "刚刚开始";
        long daysUntilStart = plan.getStartDate() == null ? -1 : ChronoUnit.DAYS.between(LocalDate.now(), plan.getStartDate());
        String timing = plan.getStartDate() == null ? "日期确定后可获得临近提醒"
                : daysUntilStart < 0 ? "旅程已经开始，按实际进度更新即可"
                : daysUntilStart == 0 ? "今天出发，记得最后核对证件与行李"
                : "距离出发还有 " + daysUntilStart + " 天";
        return new Readiness(score, level, timing, improvements.stream().limit(4).toList());
    }

    private int categoryScore(List<TripChecklistItem> checklist, TripChecklistCategory category,
            String improvement, List<String> improvements) {
        boolean exists = checklist.stream().anyMatch(item -> item.getCategory() == category);
        if (!exists) improvements.add(improvement);
        return exists ? 5 : 0;
    }

    public record Readiness(int score, String level, String timing, List<String> improvements) {
    }
}

package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.User;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.TravelReportService;
import com.example.travelfootprint.service.TravelReportService.ReportPeriod;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TravelReportController {

    private final CurrentUserService currentUserService;
    private final TravelReportService travelReportService;

    public TravelReportController(
            CurrentUserService currentUserService,
            TravelReportService travelReportService) {
        this.currentUserService = currentUserService;
        this.travelReportService = travelReportService;
    }

    @GetMapping("/reports")
    public String report(
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(required = false) String date,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先登录，再查看旅行报告。");
            return "redirect:/login";
        }

        LocalDate anchorDate = parseDate(date);
        ReportPeriod reportPeriod = ReportPeriod.from(period);
        TravelReportService.TravelReport report = travelReportService.build(currentUser, reportPeriod, anchorDate);
        TravelReportService.TravelReport previousReport = travelReportService.build(
                currentUser, reportPeriod, LocalDate.parse(report.previousDate()));
        model.addAttribute("report", report);
        model.addAttribute("previousReport", previousReport);
        model.addAttribute("comparisonItems", comparisons(report, previousReport));
        model.addAttribute("reportPeriods", ReportPeriod.values());
        return "travel-report";
    }

    private List<MetricComparison> comparisons(
            TravelReportService.TravelReport current,
            TravelReportService.TravelReport previous) {
        return List.of(
                compare("足迹", current.postCount(), previous.postCount(), "条"),
                compare("旅行日", current.travelDays(), previous.travelDays(), "天"),
                compare("覆盖省份", current.provinceCount(), previous.provinceCount(), "个"),
                compareMoney(current.expenseTotal(), previous.expenseTotal()));
    }

    private MetricComparison compare(String label, long current, long previous, String unit) {
        long delta = current - previous;
        return new MetricComparison(label, current + unit, previous + unit, signed(delta, unit), Long.compare(delta, 0));
    }

    private MetricComparison compareMoney(BigDecimal current, BigDecimal previous) {
        BigDecimal delta = current.subtract(previous);
        String deltaText = (delta.signum() > 0 ? "+" : "") + "¥" + delta.stripTrailingZeros().toPlainString();
        return new MetricComparison(
                "旅行支出",
                "¥" + current.stripTrailingZeros().toPlainString(),
                "¥" + previous.stripTrailingZeros().toPlainString(),
                deltaText,
                delta.signum());
    }

    private String signed(long value, String unit) {
        return (value > 0 ? "+" : "") + value + unit;
    }

    public record MetricComparison(String label, String current, String previous, String delta, int direction) {
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            return LocalDate.now();
        }
    }
}

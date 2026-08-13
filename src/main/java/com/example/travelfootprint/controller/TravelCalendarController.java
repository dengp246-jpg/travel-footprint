package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.User;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.TravelCalendarService;
import jakarta.servlet.http.HttpSession;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TravelCalendarController {

    private final CurrentUserService currentUserService;
    private final TravelCalendarService calendarService;

    public TravelCalendarController(CurrentUserService currentUserService, TravelCalendarService calendarService) {
        this.currentUserService = currentUserService;
        this.calendarService = calendarService;
    }

    @GetMapping("/calendar")
    public String calendar(
            @RequestParam(required = false) String month,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先登录，再查看旅行日历。");
            return "redirect:/login";
        }
        model.addAttribute("calendar", calendarService.build(currentUser, parseMonth(month)));
        return "travel-calendar";
    }

    private YearMonth parseMonth(String value) {
        if (value == null || value.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException exception) {
            return YearMonth.now();
        }
    }
}

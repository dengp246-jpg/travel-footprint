package com.example.travelfootprint.service;

import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.TravelPostRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TravelCalendarService {

    private final TravelPostRepository postRepository;

    public TravelCalendarService(TravelPostRepository postRepository) {
        this.postRepository = postRepository;
    }

    public CalendarView build(User user, YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        LocalDate gridStart = monthStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate gridEnd = monthEnd.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        Map<LocalDate, List<TravelPost>> postsByDate = postRepository
                .findByAuthorIdAndTravelDateBetweenOrderByTravelDateAscCreatedAtAsc(user.getId(), gridStart, gridEnd)
                .stream()
                .collect(Collectors.groupingBy(TravelPost::getTravelDate));

        List<CalendarDay> days = new ArrayList<>();
        for (LocalDate date = gridStart; !date.isAfter(gridEnd); date = date.plusDays(1)) {
            List<TravelPost> posts = postsByDate.getOrDefault(date, List.of());
            days.add(new CalendarDay(date, date.getMonth().equals(month.getMonth()), date.equals(LocalDate.now()), posts));
        }
        long postCount = days.stream().filter(CalendarDay::inCurrentMonth).mapToLong(day -> day.posts().size()).sum();
        long travelDays = days.stream().filter(CalendarDay::inCurrentMonth).filter(day -> !day.posts().isEmpty()).count();
        return new CalendarView(
                month,
                month.getYear() + "年" + month.getMonthValue() + "月",
                month.minusMonths(1).toString(),
                month.plusMonths(1).toString(),
                postCount,
                travelDays,
                days);
    }

    public record CalendarView(
            YearMonth month,
            String title,
            String previousMonth,
            String nextMonth,
            long postCount,
            long travelDays,
            List<CalendarDay> days) {
    }

    public record CalendarDay(LocalDate date, boolean inCurrentMonth, boolean today, List<TravelPost> posts) {
    }
}

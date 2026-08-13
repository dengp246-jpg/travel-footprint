package com.example.travelfootprint.service;

import com.example.travelfootprint.model.ExpenseCategory;
import com.example.travelfootprint.model.TravelExpense;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.TravelExpenseRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TravelExpenseService {

    private final TravelExpenseRepository expenseRepository;

    public TravelExpenseService(TravelExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    public ExpenseView build(User user, YearMonth month) {
        List<TravelExpense> expenses = expenseRepository
                .findByOwnerIdAndOccurredOnBetweenOrderByOccurredOnDescCreatedAtDesc(
                        user.getId(), month.atDay(1), month.atEndOfMonth());
        BigDecimal total = expenses.stream().map(TravelExpense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<ExpenseCategory, BigDecimal> totals = expenses.stream().collect(Collectors.groupingBy(
                TravelExpense::getCategory,
                Collectors.reducing(BigDecimal.ZERO, TravelExpense::getAmount, BigDecimal::add)));
        List<CategoryTotal> categories = Arrays.stream(ExpenseCategory.values())
                .map(category -> {
                    BigDecimal amount = totals.getOrDefault(category, BigDecimal.ZERO);
                    double percentage = total.signum() == 0 ? 0
                            : amount.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP).doubleValue();
                    return new CategoryTotal(category, amount, percentage);
                })
                .filter(item -> item.amount().signum() > 0)
                .sorted((left, right) -> right.amount().compareTo(left.amount()))
                .toList();
        return new ExpenseView(
                month.getYear() + "年" + month.getMonthValue() + "月",
                month.minusMonths(1).toString(),
                month.plusMonths(1).toString(),
                total,
                expenses.size(),
                categories,
                expenses);
    }

    public record ExpenseView(
            String title,
            String previousMonth,
            String nextMonth,
            BigDecimal total,
            long count,
            List<CategoryTotal> categories,
            List<TravelExpense> expenses) {
    }

    public record CategoryTotal(ExpenseCategory category, BigDecimal amount, double percentage) {
    }
}

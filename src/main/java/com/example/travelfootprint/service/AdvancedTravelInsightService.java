package com.example.travelfootprint.service;

import com.example.travelfootprint.model.TravelExpense;
import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.TravelExpenseRepository;
import com.example.travelfootprint.repository.TravelPostRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AdvancedTravelInsightService {

    private final TravelPostRepository postRepository;
    private final TravelExpenseRepository expenseRepository;
    private final ProvinceCatalogService provinceCatalogService;
    private final LocationNormalizationService locationService;

    public AdvancedTravelInsightService(
            TravelPostRepository postRepository,
            TravelExpenseRepository expenseRepository,
            ProvinceCatalogService provinceCatalogService,
            LocationNormalizationService locationService) {
        this.postRepository = postRepository;
        this.expenseRepository = expenseRepository;
        this.provinceCatalogService = provinceCatalogService;
        this.locationService = locationService;
    }

    public InsightView build(User user) {
        List<TravelPost> posts = postRepository.findByAuthorIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(post -> post.getTravelDate() != null).toList();
        List<TravelExpense> expenses = expenseRepository.findByOwnerIdOrderByOccurredOnDescCreatedAtDesc(user.getId());
        Set<String> provinces = posts.stream().map(this::province).filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> locations = posts.stream().map(locationService::normalizeLookupKey).filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> categories = posts.stream().map(post -> value(post.getCategory(), "旅行灵感"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<LocalDate> travelDates = posts.stream().map(TravelPost::getTravelDate)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<LocalDate> sortedDates = travelDates.stream().sorted().toList();
        long averageGap = averageGap(sortedDates);
        double revisitRate = revisitRate(posts);
        List<Distribution> seasons = distributions(posts, post -> season(post.getTravelDate().getMonth()));
        int seasonCount = (int) seasons.stream().filter(item -> item.count() > 0).count();
        int diversityScore = Math.min(35, provinces.size() * 7)
                + Math.min(25, categories.size() * 5)
                + seasonCount * 5
                + (posts.isEmpty() ? 0 : (int) Math.round(locations.size() * 20.0 / posts.size()));
        diversityScore = Math.min(100, diversityScore);

        int year = LocalDate.now().getYear();
        List<MonthlyPulse> pulse = monthlyPulse(posts, year);
        BigDecimal totalExpense = expenses.stream().map(TravelExpense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal perDay = travelDates.isEmpty() ? BigDecimal.ZERO
                : totalExpense.divide(BigDecimal.valueOf(travelDates.size()), 0, RoundingMode.HALF_UP);
        List<Distribution> expenseCategories = expenseDistributions(expenses);
        List<Distribution> travelCategories = distributions(posts, post -> value(post.getCategory(), "旅行灵感"));
        String personality = personality(travelCategories, diversityScore, revisitRate);
        List<String> actions = actions(posts, expenses, provinces.size(), categories.size(), seasons, revisitRate, averageGap);
        int nextMilestone = nextMilestone(provinces.size());
        int milestoneProgress = nextMilestone == 0 ? 100 : (int) Math.min(100, Math.round(provinces.size() * 100.0 / nextMilestone));

        return new InsightView(
                personality, diversityScore, posts.size(), locations.size(), provinces.size(), travelDates.size(),
                Math.round(revisitRate * 10.0) / 10.0, averageGap, totalExpense, perDay,
                year, pulse, seasons, travelCategories.stream().limit(6).toList(), expenseCategories,
                actions, nextMilestone, milestoneProgress);
    }

    private long averageGap(List<LocalDate> dates) {
        if (dates.size() < 2) return 0;
        long total = 0;
        for (int i = 1; i < dates.size(); i++) total += ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i));
        return Math.round(total * 1.0 / (dates.size() - 1));
    }

    private double revisitRate(List<TravelPost> posts) {
        Set<String> seen = new LinkedHashSet<>();
        long revisits = 0;
        List<TravelPost> chronological = posts.stream().sorted(Comparator.comparing(TravelPost::getTravelDate)).toList();
        for (TravelPost post : chronological) {
            String key = locationService.normalizeLookupKey(post);
            if (!key.isBlank() && !seen.add(key)) revisits++;
        }
        return posts.isEmpty() ? 0 : revisits * 100.0 / posts.size();
    }

    private List<MonthlyPulse> monthlyPulse(List<TravelPost> posts, int year) {
        Map<Integer, Long> counts = posts.stream().filter(post -> post.getTravelDate().getYear() == year)
                .collect(Collectors.groupingBy(post -> post.getTravelDate().getMonthValue(), Collectors.counting()));
        long max = counts.values().stream().mapToLong(Long::longValue).max().orElse(1);
        List<MonthlyPulse> result = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            long count = counts.getOrDefault(month, 0L);
            result.add(new MonthlyPulse(month + "月", count, Math.max(5, Math.round(count * 100.0 / max))));
        }
        return result;
    }

    private List<Distribution> distributions(List<TravelPost> posts, Function<TravelPost, String> classifier) {
        Map<String, Long> counts = posts.stream().map(classifier)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        return rank(counts, posts.size());
    }

    private List<Distribution> expenseDistributions(List<TravelExpense> expenses) {
        Map<String, Long> counts = expenses.stream().collect(Collectors.groupingBy(
                expense -> expense.getCategory().getLabel(), LinkedHashMap::new, Collectors.counting()));
        return rank(counts, expenses.size());
    }

    private List<Distribution> rank(Map<String, Long> counts, int total) {
        return counts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(item -> new Distribution(item.getKey(), item.getValue(),
                        total == 0 ? 0 : Math.round(item.getValue() * 100.0 / total))).toList();
    }

    private String personality(List<Distribution> categories, int diversity, double revisitRate) {
        if (categories.isEmpty()) return "等待启程的旅行者";
        String theme = categories.get(0).label();
        if (diversity >= 75) return "跨界探索型 · " + theme;
        if (revisitRate >= 35) return "深度重游型 · " + theme;
        return "主题发现型 · " + theme;
    }

    private List<String> actions(List<TravelPost> posts, List<TravelExpense> expenses, int provinceCount,
            int categoryCount, List<Distribution> seasons, double revisitRate, long averageGap) {
        List<String> actions = new ArrayList<>();
        if (posts.isEmpty()) actions.add("发布第一条足迹，开始建立个人旅行画像");
        if (provinceCount < 5) actions.add("下一站尝试一个从未记录过的省份，提升地域多样性");
        if (categoryCount < 3) actions.add("尝试自然、人文或美食之外的新主题");
        if (seasons.size() < 4) actions.add("补上一段尚未记录季节的旅程，让年度轨迹更完整");
        if (revisitRate > 45) actions.add("近期重游比例较高，可以在熟悉目的地加入一个新地点");
        if (averageGap > 60) actions.add("平均旅行间隔超过两个月，可先安排一次短途周末旅行");
        if (expenses.isEmpty()) actions.add("记录旅行支出，解锁日均成本和消费结构洞察");
        if (actions.isEmpty()) actions.add("旅行结构很均衡，继续保持记录并及时整理照片与账本");
        return actions.stream().limit(5).toList();
    }

    private int nextMilestone(int provinceCount) {
        for (int target : List.of(5, 10, 20, 34)) if (provinceCount < target) return target;
        return 0;
    }

    private String province(TravelPost post) {
        return provinceCatalogService.resolveProvince(post.getProvince(), post.getLocation()).orElse("");
    }

    private String season(Month month) {
        int value = month.getValue();
        return value <= 2 || value == 12 ? "冬季" : value <= 5 ? "春季" : value <= 8 ? "夏季" : "秋季";
    }

    private String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

    public record InsightView(
            String personality, int diversityScore, int footprintCount, int locationCount, int provinceCount,
            int travelDayCount, double revisitRate, long averageGapDays, BigDecimal totalExpense, BigDecimal expensePerDay,
            int pulseYear, List<MonthlyPulse> monthlyPulse, List<Distribution> seasons,
            List<Distribution> travelCategories, List<Distribution> expenseCategories,
            List<String> actions, int nextProvinceMilestone, int milestoneProgress) { }
    public record MonthlyPulse(String label, long count, long height) { }
    public record Distribution(String label, long count, long percentage) { }
}

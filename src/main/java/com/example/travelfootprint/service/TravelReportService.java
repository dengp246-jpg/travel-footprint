package com.example.travelfootprint.service;

import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.model.TravelExpense;
import com.example.travelfootprint.repository.TravelExpenseRepository;
import com.example.travelfootprint.repository.TravelPostRepository;
import java.time.DayOfWeek;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TravelReportService {

    private static final DateTimeFormatter DATE_PARAMETER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("M月d日");

    private final TravelPostRepository postRepository;
    private final TravelExpenseRepository expenseRepository;
    private final DestinationMapService destinationMapService;
    private final LocationNormalizationService locationNormalizationService;
    private final ProvinceCatalogService provinceCatalogService;

    public TravelReportService(
            TravelPostRepository postRepository,
            TravelExpenseRepository expenseRepository,
            DestinationMapService destinationMapService,
            LocationNormalizationService locationNormalizationService,
            ProvinceCatalogService provinceCatalogService) {
        this.postRepository = postRepository;
        this.expenseRepository = expenseRepository;
        this.destinationMapService = destinationMapService;
        this.locationNormalizationService = locationNormalizationService;
        this.provinceCatalogService = provinceCatalogService;
    }

    public TravelReport build(User user, ReportPeriod period, LocalDate anchorDate) {
        LocalDate today = LocalDate.now();
        LocalDate safeAnchor = anchorDate == null || anchorDate.isAfter(today) ? today : anchorDate;
        DateRange range = resolveRange(period, safeAnchor);
        List<TravelPost> posts = postRepository.findByAuthorIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(post -> post.getTravelDate() != null)
                .filter(post -> !post.getTravelDate().isBefore(range.start())
                        && !post.getTravelDate().isAfter(range.end()))
                .sorted(Comparator.comparing(TravelPost::getTravelDate)
                        .thenComparing(TravelPost::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        long locationCount = posts.stream()
                .map(locationNormalizationService::normalizeDisplayLocation)
                .filter(value -> !value.isBlank())
                .distinct()
                .count();
        long provinceCount = posts.stream()
                .map(post -> provinceCatalogService.resolveProvince(post.getProvince(), post.getLocation()).orElse(""))
                .filter(value -> !value.isBlank())
                .distinct()
                .count();
        long travelDays = posts.stream().map(TravelPost::getTravelDate).distinct().count();
        long photoCount = posts.stream().filter(post -> post.getPhotoPath() != null && !post.getPhotoPath().isBlank()).count();
        List<TravelExpense> expenses = expenseRepository
                .findByOwnerIdAndOccurredOnBetweenOrderByOccurredOnDescCreatedAtDesc(user.getId(), range.start(), range.end());
        BigDecimal expenseTotal = expenses.stream().map(TravelExpense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<DistributionItem> categories = distribution(posts, post -> valueOr(post.getCategory(), "未分类"));
        List<DistributionItem> provinces = distribution(posts, post ->
                provinceCatalogService.resolveProvince(post.getProvince(), post.getLocation()).orElse("待确认"));
        List<ActivityBucket> activity = activityBuckets(period, range, posts);
        List<TravelPost> featuredPhotos = posts.stream()
                .filter(post -> post.getPhotoPath() != null && !post.getPhotoPath().isBlank())
                .sorted(Comparator.comparing(TravelPost::getTravelDate).reversed())
                .limit(6)
                .toList();
        List<TravelPost> timeline = posts.stream()
                .sorted(Comparator.comparing(TravelPost::getTravelDate).reversed()
                        .thenComparing(TravelPost::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(12)
                .toList();
        MapRoute mapRoute = buildMapRoute(posts);

        String insight = buildInsight(posts, categories, provinces, travelDays);
        String title = reportTitle(period, range);
        boolean hasNext = range.end().isBefore(today);
        return new TravelReport(
                period,
                title,
                period.kicker,
                range.start(),
                range.end(),
                range.start().minusDays(1).format(DATE_PARAMETER),
                range.end().plusDays(1).format(DATE_PARAMETER),
                hasNext,
                posts.size(),
                locationCount,
                provinceCount,
                travelDays,
                photoCount,
                expenses.size(),
                expenseTotal,
                Math.round(provinceCount * 1000.0 / 34.0) / 10.0,
                insight,
                activity,
                categories,
                provinces,
                featuredPhotos,
                timeline,
                mapRoute.points(),
                mapRoute.polyline());
    }

    private DateRange resolveRange(ReportPeriod period, LocalDate anchor) {
        return switch (period) {
            case WEEK -> {
                LocalDate start = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new DateRange(start, start.plusDays(6));
            }
            case MONTH -> new DateRange(anchor.withDayOfMonth(1), anchor.with(TemporalAdjusters.lastDayOfMonth()));
            case YEAR -> new DateRange(anchor.withDayOfYear(1), anchor.with(TemporalAdjusters.lastDayOfYear()));
        };
    }

    private String reportTitle(ReportPeriod period, DateRange range) {
        return switch (period) {
            case WEEK -> range.start().format(SHORT_DATE) + " — " + range.end().format(SHORT_DATE);
            case MONTH -> range.start().getYear() + "年" + range.start().getMonthValue() + "月";
            case YEAR -> range.start().getYear() + "年度";
        };
    }

    private List<DistributionItem> distribution(
            List<TravelPost> posts,
            Function<TravelPost, String> classifier) {
        if (posts.isEmpty()) {
            return List.of();
        }
        Map<String, Long> counts = posts.stream()
                .map(classifier)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        long maximum = counts.values().stream().mapToLong(Long::longValue).max().orElse(1L);
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> new DistributionItem(
                        entry.getKey(),
                        entry.getValue(),
                        Math.round(entry.getValue() * 1000.0 / posts.size()) / 10.0,
                        Math.round(entry.getValue() * 100.0 / maximum)))
                .toList();
    }

    private List<ActivityBucket> activityBuckets(
            ReportPeriod period,
            DateRange range,
            List<TravelPost> posts) {
        Map<Integer, Long> counts = posts.stream().collect(Collectors.groupingBy(
                post -> activityIndex(period, range, post.getTravelDate()),
                Collectors.counting()));
        int bucketCount = switch (period) {
            case WEEK -> 7;
            case MONTH -> range.end().getDayOfMonth();
            case YEAR -> 12;
        };
        long maximum = counts.values().stream().mapToLong(Long::longValue).max().orElse(1L);
        List<ActivityBucket> buckets = new ArrayList<>();
        for (int index = 0; index < bucketCount; index++) {
            long count = counts.getOrDefault(index, 0L);
            String label = switch (period) {
                case WEEK -> DayOfWeek.of(index + 1).getDisplayName(TextStyle.SHORT, Locale.CHINA);
                case MONTH -> String.valueOf(index + 1);
                case YEAR -> (index + 1) + "月";
            };
            double intensity = count == 0 ? 0.04 : 0.22 + (double) count / maximum * 0.78;
            buckets.add(new ActivityBucket(label, count, intensity));
        }
        return buckets;
    }

    private int activityIndex(ReportPeriod period, DateRange range, LocalDate date) {
        return switch (period) {
            case WEEK -> date.getDayOfWeek().getValue() - 1;
            case MONTH -> date.getDayOfMonth() - 1;
            case YEAR -> date.getMonthValue() - 1;
        };
    }

    private MapRoute buildMapRoute(List<TravelPost> posts) {
        Map<String, ReportMapPoint> uniquePoints = new LinkedHashMap<>();
        posts.forEach(post -> destinationMapService.resolvePlacement(post).ifPresent(placement -> {
            String province = provinceCatalogService.resolveProvince(post.getProvince(), post.getLocation()).orElse("待确认");
            String key = province + "|" + placement.groupKey();
            uniquePoints.putIfAbsent(key, new ReportMapPoint(
                    post.getId(),
                    post.getTitle(),
                    province,
                    locationNormalizationService.normalizeDisplayLocation(post),
                    post.getTravelDate(),
                    placement.point().left(),
                    placement.point().top()));
        }));
        List<ReportMapPoint> points = List.copyOf(uniquePoints.values());
        StringJoiner polyline = new StringJoiner(" ");
        points.forEach(point -> polyline.add(point.left() + "," + point.top()));
        return new MapRoute(points, polyline.toString());
    }

    private String buildInsight(
            List<TravelPost> posts,
            List<DistributionItem> categories,
            List<DistributionItem> provinces,
            long travelDays) {
        if (posts.isEmpty()) {
            return "这个周期还没有旅行记录。下一段旅程，正等着你去点亮。";
        }
        String province = provinces.isEmpty() ? "新的地方" : provinces.get(0).name();
        String category = categories.isEmpty() ? "旅行探索" : categories.get(0).name();
        return "你用 " + travelDays + " 个旅行日留下了 " + posts.size() + " 条足迹，"
                + province + " 是最常出现的目的地，" + category + " 是本期的旅行主题。";
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public enum ReportPeriod {
        WEEK("week", "WEEKLY JOURNEY", "周报"),
        MONTH("month", "MONTHLY JOURNEY", "月报"),
        YEAR("year", "YEARLY JOURNEY", "年报");

        private final String value;
        private final String kicker;
        private final String label;

        ReportPeriod(String value, String kicker, String label) {
            this.value = value;
            this.kicker = kicker;
            this.label = label;
        }

        public String getValue() {
            return value;
        }

        public String getKicker() {
            return kicker;
        }

        public String getLabel() {
            return label;
        }

        public static ReportPeriod from(String value) {
            for (ReportPeriod period : values()) {
                if (period.value.equalsIgnoreCase(value)) {
                    return period;
                }
            }
            return MONTH;
        }
    }

    public record TravelReport(
            ReportPeriod period,
            String title,
            String kicker,
            LocalDate startDate,
            LocalDate endDate,
            String previousDate,
            String nextDate,
            boolean hasNext,
            long postCount,
            long locationCount,
            long provinceCount,
            long travelDays,
            long photoCount,
            long expenseCount,
            BigDecimal expenseTotal,
            double coveragePercent,
            String insight,
            List<ActivityBucket> activity,
            List<DistributionItem> categories,
            List<DistributionItem> provinces,
            List<TravelPost> featuredPhotos,
            List<TravelPost> timeline,
            List<ReportMapPoint> mapPoints,
            String routePolyline) {
    }

    public record DistributionItem(String name, long count, double percentage, long width) {
    }

    public record ActivityBucket(String label, long count, double intensity) {
    }

    public record ReportMapPoint(
            Long postId,
            String title,
            String province,
            String location,
            LocalDate travelDate,
            double left,
            double top) {
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }

    private record MapRoute(List<ReportMapPoint> points, String polyline) {
    }
}

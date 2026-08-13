package com.example.travelfootprint.service;

import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.TripPlan;
import com.example.travelfootprint.model.TripPlanActivity;
import com.example.travelfootprint.repository.TravelPostRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SmartItineraryService {

    private static final List<LocalTime> TIMES = List.of(LocalTime.of(9, 0), LocalTime.of(14, 30));

    private final TravelPostRepository postRepository;
    private final ContentVisibilityService visibilityService;
    private final ProvinceCatalogService provinceCatalogService;
    private final LocationNormalizationService locationService;
    private final DestinationMapService destinationMapService;

    public SmartItineraryService(
            TravelPostRepository postRepository,
            ContentVisibilityService visibilityService,
            ProvinceCatalogService provinceCatalogService,
            LocationNormalizationService locationService,
            DestinationMapService destinationMapService) {
        this.postRepository = postRepository;
        this.visibilityService = visibilityService;
        this.provinceCatalogService = provinceCatalogService;
        this.locationService = locationService;
        this.destinationMapService = destinationMapService;
    }

    public List<Suggestion> suggest(TripPlan plan, List<TripPlanActivity> existing) {
        String province = provinceCatalogService.resolveProvince("", plan.getDestination()).orElse("");
        String destinationKey = key(plan.getDestination());
        Map<String, Candidate> candidates = new LinkedHashMap<>();

        List<TravelPost> posts = visibilityService.approvedPosts(postRepository.findAllByOrderByCreatedAtDesc());
        for (TravelPost post : posts) {
            String postProvince = provinceCatalogService.resolveProvince(post.getProvince(), post.getLocation()).orElse("");
            boolean relevant = !province.isBlank() ? province.equals(postProvince)
                    : key(post.getLocation()).contains(destinationKey) || destinationKey.contains(key(post.getLocation()));
            if (!relevant) continue;
            String location = locationService.primaryLocationSegment(post);
            if (location.isBlank()) location = post.getLocation();
            String source = post.getSourceName() == null || post.getSourceName().isBlank()
                    ? "公开旅行足迹" : post.getSourceName();
            candidates.putIfAbsent(key(location), new Candidate(location, "探索「" + compact(post.getTitle(), 22) + "」",
                    "根据“" + source + "”中的公开内容推荐，请结合开放时间与实际路况调整。", "公开足迹"));
        }
        destinationMapService.locationSuggestions().stream()
                .filter(place -> !province.isBlank() ? province.equals(place.province())
                        : place.searchText().contains(destinationKey))
                .forEach(place -> candidates.putIfAbsent(key(place.location()),
                        new Candidate(place.location(), "漫游 " + place.location(),
                                "来自本地地点目录，建议出发前确认预约、天气与交通信息。", "地点目录")));
        if (candidates.isEmpty()) {
            candidates.put(key(plan.getDestination()), new Candidate(plan.getDestination(),
                    "初识 " + plan.getDestination(), "先安排一段轻松漫游，为临场发现保留时间。", "行程目的地"));
        }

        Set<String> existingKeys = new LinkedHashSet<>();
        existing.forEach(item -> {
            existingKeys.add(key(item.getLocation()));
            existingKeys.add(key(item.getTitle()));
        });
        LocalDate start = plan.getStartDate() == null ? LocalDate.now().plusDays(7) : plan.getStartDate();
        LocalDate end = plan.getEndDate() == null || plan.getEndDate().isBefore(start) ? start.plusDays(2) : plan.getEndDate();
        List<Candidate> pool = new ArrayList<>(candidates.values());
        List<Suggestion> suggestions = new ArrayList<>();
        int slot = 0;
        for (Candidate candidate : pool) {
            if (existingKeys.contains(key(candidate.location())) || existingKeys.contains(key(candidate.title()))) continue;
            LocalDate date = start.plusDays(slot / TIMES.size());
            if (date.isAfter(end) || suggestions.size() >= 8) break;
            LocalTime time = TIMES.get(slot % TIMES.size());
            String suggestionKey = digest(plan.getId() + "|" + date + "|" + time + "|" + candidate.title() + "|" + candidate.location());
            suggestions.add(new Suggestion(suggestionKey, date, time, candidate.title(), candidate.location(),
                    candidate.note(), candidate.reason()));
            slot++;
        }
        return suggestions;
    }

    private String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes, 0, 10);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String compact(String value, int max) {
        if (value == null || value.isBlank()) return "当地灵感";
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private String key(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[\\s·,，。/\\\\-]", "");
    }

    private record Candidate(String location, String title, String note, String reason) {
    }

    public record Suggestion(
            String key,
            LocalDate date,
            LocalTime time,
            String title,
            String location,
            String note,
            String reason) {
    }
}

package com.example.travelfootprint.service;

import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.repository.TravelPostRepository;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DestinationGuideService {

    private final TravelPostRepository postRepository;
    private final ContentVisibilityService visibilityService;
    private final ProvinceCatalogService provinceCatalogService;
    private final LocationNormalizationService locationService;
    private final DestinationMapService destinationMapService;

    public DestinationGuideService(
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

    public GuideView build(String requestedProvince, String query) {
        String selectedProvince = provinceCatalogService.normalizeProvince(requestedProvince).orElse("");
        String keyword = query == null ? "" : query.trim();
        String lookup = keyword.toLowerCase(Locale.ROOT).replace(" ", "");
        List<TravelPost> publicPosts = visibilityService.approvedPosts(postRepository.findAllByOrderByCreatedAtDesc());

        List<TravelPost> scopedPosts = publicPosts.stream()
                .filter(post -> selectedProvince.isBlank() || selectedProvince.equals(resolveProvince(post)))
                .filter(post -> matches(post, lookup))
                .toList();
        List<DestinationMapService.LocationSuggestion> places = destinationMapService.locationSuggestions().stream()
                .filter(place -> selectedProvince.isBlank() || selectedProvince.equals(place.province()))
                .filter(place -> lookup.isBlank() || place.searchText().replace(" ", "").contains(lookup))
                .limit(10)
                .toList();

        Map<String, Long> locationCounts = count(scopedPosts.stream()
                .map(locationService::primaryLocationSegment).filter(value -> !value.isBlank()).toList());
        Map<String, Long> categoryCounts = count(scopedPosts.stream()
                .map(post -> post.getCategory() == null || post.getCategory().isBlank() ? "旅行灵感" : post.getCategory())
                .toList());
        Map<String, Long> provinceCounts = count(publicPosts.stream().map(this::resolveProvince)
                .filter(value -> !value.isBlank()).toList());
        long referenceCount = scopedPosts.stream().filter(post -> post.getSourceUrl() != null && !post.getSourceUrl().isBlank()).count();
        int uniquePlaces = (int) scopedPosts.stream().map(locationService::normalizeLookupKey)
                .filter(value -> !value.isBlank()).distinct().count();

        return new GuideView(
                provinceCatalogService.provinceNames(), selectedProvince, keyword,
                scopedPosts.stream().limit(12).toList(), places,
                ranked(locationCounts, 8), ranked(categoryCounts, 6), ranked(provinceCounts, 8),
                scopedPosts.size(), uniquePlaces, referenceCount);
    }

    private boolean matches(TravelPost post, String lookup) {
        if (lookup.isBlank()) return true;
        return String.join(" ", safe(post.getTitle()), safe(post.getLocation()), safe(post.getProvince()),
                        safe(post.getContent()), safe(post.getTags()), safe(post.getCategory()))
                .toLowerCase(Locale.ROOT).replace(" ", "").contains(lookup);
    }

    private String resolveProvince(TravelPost post) {
        return provinceCatalogService.resolveProvince(post.getProvince(), post.getLocation()).orElse("");
    }

    private Map<String, Long> count(List<String> values) {
        Map<String, Long> counts = new LinkedHashMap<>();
        values.forEach(value -> counts.merge(value, 1L, Long::sum));
        return counts;
    }

    private List<RankItem> ranked(Map<String, Long> counts, int limit) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry::getKey))
                .limit(limit).map(entry -> new RankItem(entry.getKey(), entry.getValue())).toList();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record GuideView(
            List<String> provinces,
            String selectedProvince,
            String query,
            List<TravelPost> posts,
            List<DestinationMapService.LocationSuggestion> places,
            List<RankItem> topLocations,
            List<RankItem> topCategories,
            List<RankItem> topProvinces,
            int footprintCount,
            int uniquePlaces,
            long referenceCount) {
    }

    public record RankItem(String label, long count) {
    }
}

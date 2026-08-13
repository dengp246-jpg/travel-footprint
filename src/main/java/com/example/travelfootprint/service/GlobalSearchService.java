package com.example.travelfootprint.service;

import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.TripPlan;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.TravelPostRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class GlobalSearchService {

    private final TravelPostRepository postRepository;
    private final ContentVisibilityService visibilityService;
    private final TripPlanAccessService planAccessService;
    private final DestinationMapService mapService;

    public GlobalSearchService(
            TravelPostRepository postRepository,
            ContentVisibilityService visibilityService,
            TripPlanAccessService planAccessService,
            DestinationMapService mapService) {
        this.postRepository = postRepository;
        this.visibilityService = visibilityService;
        this.planAccessService = planAccessService;
        this.mapService = mapService;
    }

    public SearchView search(User viewer, String query, String type) {
        String safeQuery = query == null ? "" : query.trim();
        String lookup = normalize(safeQuery);
        String selectedType = List.of("all", "posts", "plans", "places").contains(type) ? type : "all";
        List<PostResult> posts = lookup.isBlank() ? List.of() : postRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(post -> visibilityService.canViewPost(viewer, post))
                .filter(post -> contains(lookup, post.getTitle(), post.getContent(), post.getLocation(), post.getProvince(),
                        post.getCategory(), post.getTags(), post.getAuthor().getNickname()))
                .limit(18)
                .map(post -> new PostResult(post,
                        post.isApproximateLocation() && (viewer == null || !post.getAuthor().getId().equals(viewer.getId()))
                                ? value(post.getProvince(), "位置已隐藏") + " · 具体位置已隐藏" : post.getLocation()))
                .toList();
        List<TripPlan> plans = viewer == null || lookup.isBlank() ? List.of() : planAccessService.visiblePlans(viewer).stream()
                .filter(plan -> contains(lookup, plan.getTitle(), plan.getDestination(), plan.getNotes(), plan.getStatus().name()))
                .limit(12).toList();
        List<DestinationMapService.LocationSuggestion> places = lookup.isBlank() ? List.of() : mapService.locationSuggestions().stream()
                .filter(place -> normalize(place.searchText()).contains(lookup) || normalize(place.location()).contains(lookup))
                .limit(18).toList();
        return new SearchView(safeQuery, selectedType, posts, plans, places, posts.size() + plans.size() + places.size());
    }

    private boolean contains(String lookup, String... values) {
        return normalize(String.join(" ", java.util.Arrays.stream(values).map(this::value).toList())).contains(lookup);
    }

    private String normalize(String value) {
        return value(value).toLowerCase(Locale.ROOT).replaceAll("[\\s·,，。/\\\\-]", "");
    }

    private String value(String value) { return value(value, ""); }
    private String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

    public record SearchView(
            String query,
            String selectedType,
            List<PostResult> posts,
            List<TripPlan> plans,
            List<DestinationMapService.LocationSuggestion> places,
            int totalCount) {
    }

    public record PostResult(TravelPost post, String displayLocation) {
    }
}

package com.example.travelfootprint.service;

import com.example.travelfootprint.model.PostFavorite;
import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.PostFavoriteRepository;
import com.example.travelfootprint.repository.RecommendationDismissalRepository;
import com.example.travelfootprint.repository.TravelPostRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PersonalizedRecommendationService {

    private final TravelPostRepository postRepository;
    private final PostFavoriteRepository favoriteRepository;
    private final RecommendationDismissalRepository dismissalRepository;
    private final ContentVisibilityService visibilityService;
    private final ProvinceCatalogService provinceCatalogService;

    public PersonalizedRecommendationService(
            TravelPostRepository postRepository,
            PostFavoriteRepository favoriteRepository,
            RecommendationDismissalRepository dismissalRepository,
            ContentVisibilityService visibilityService,
            ProvinceCatalogService provinceCatalogService) {
        this.postRepository = postRepository;
        this.favoriteRepository = favoriteRepository;
        this.dismissalRepository = dismissalRepository;
        this.visibilityService = visibilityService;
        this.provinceCatalogService = provinceCatalogService;
    }

    public RecommendationView build(User user) {
        List<TravelPost> own = postRepository.findByAuthorIdOrderByCreatedAtDesc(user.getId());
        List<PostFavorite> favorites = favoriteRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        Map<String, Integer> categoryWeights = new LinkedHashMap<>();
        Map<String, Integer> provinceWeights = new LinkedHashMap<>();
        own.forEach(post -> addPreferences(post, categoryWeights, provinceWeights, 2));
        favorites.forEach(item -> addPreferences(item.getPost(), categoryWeights, provinceWeights, 3));
        Set<Long> favoriteIds = favorites.stream().map(item -> item.getPost().getId()).collect(java.util.stream.Collectors.toSet());
        Set<Long> dismissedIds = dismissalRepository.findByUserId(user.getId()).stream()
                .map(item -> item.getPost().getId()).collect(java.util.stream.Collectors.toSet());
        List<Recommendation> recommendations = visibilityService.approvedPosts(postRepository.findAllByOrderByCreatedAtDesc()).stream()
                .filter(post -> !post.getAuthor().getId().equals(user.getId()))
                .filter(post -> !favoriteIds.contains(post.getId()) && !dismissedIds.contains(post.getId()))
                .map(post -> score(post, categoryWeights, provinceWeights))
                .sorted(Comparator.comparingInt(Recommendation::score).reversed()
                        .thenComparing(item -> item.post().getCreatedAt(), Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(12).toList();
        List<Preference> categories = ranked(categoryWeights, 5);
        List<Preference> provinces = ranked(provinceWeights, 5);
        boolean coldStart = categoryWeights.isEmpty() && provinceWeights.isEmpty();
        return new RecommendationView(recommendations, categories, provinces, coldStart, dismissedIds.size());
    }

    private Recommendation score(TravelPost post, Map<String, Integer> categories, Map<String, Integer> provinces) {
        String category = value(post.getCategory(), "旅行灵感");
        String province = resolveProvince(post);
        int categoryScore = categories.getOrDefault(category, 0);
        int provinceScore = provinces.getOrDefault(province, 0);
        int score = 10 + categoryScore * 4 + provinceScore * 3;
        List<String> reasons = new ArrayList<>();
        if (categoryScore > 0) reasons.add("符合你偏爱的“" + category + "”主题");
        if (provinceScore > 0 && !province.isBlank()) reasons.add("延伸你在“" + province + "”的旅行兴趣");
        if (reasons.isEmpty()) reasons.add("来自近期公开旅行灵感");
        if (post.getSourceName() != null && !post.getSourceName().isBlank()) reasons.add("包含可核对的参考来源");
        return new Recommendation(post, Math.min(99, score), String.join("；", reasons));
    }

    private void addPreferences(TravelPost post, Map<String, Integer> categories, Map<String, Integer> provinces, int weight) {
        categories.merge(value(post.getCategory(), "旅行灵感"), weight, Integer::sum);
        String province = resolveProvince(post);
        if (!province.isBlank()) provinces.merge(province, weight, Integer::sum);
    }

    private String resolveProvince(TravelPost post) {
        return provinceCatalogService.resolveProvince(post.getProvince(), post.getLocation()).orElse("");
    }

    private List<Preference> ranked(Map<String, Integer> weights, int limit) {
        return weights.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit).map(item -> new Preference(item.getKey(), item.getValue())).toList();
    }

    private String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

    public record RecommendationView(
            List<Recommendation> recommendations,
            List<Preference> categories,
            List<Preference> provinces,
            boolean coldStart,
            int dismissedCount) {
    }
    public record Recommendation(TravelPost post, int score, String reason) { }
    public record Preference(String label, int weight) { }
}

package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.TravelPostRepository;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.DestinationMapService;
import com.example.travelfootprint.service.ProvinceCatalogService;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MapController {

    private static final String MODE_PUBLIC = "public";
    private static final String MODE_PERSONAL = "personal";
    private static final String SESSION_MAP_MODE = "preferredMapMode";
    private static final Map<String, ProvinceFocusView> PROVINCE_FOCUSES = Map.ofEntries(
            Map.entry("北京", focus("北京", 68.71, 41.22, 4.2)),
            Map.entry("天津", focus("天津", 69.42, 42.85, 4.2)),
            Map.entry("河北", focus("河北", 65.42, 44.92, 3.15)),
            Map.entry("山西", focus("山西", 62.50, 45.26, 3.0)),
            Map.entry("内蒙古", focus("内蒙古", 61.30, 39.36, 2.0)),
            Map.entry("辽宁", focus("辽宁", 78.69, 37.45, 3.0)),
            Map.entry("吉林", focus("吉林", 81.51, 33.46, 2.75)),
            Map.entry("黑龙江", focus("黑龙江", 83.31, 29.52, 2.35)),
            Map.entry("上海", focus("上海", 75.78, 58.43, 4.5)),
            Map.entry("江苏", focus("江苏", 71.80, 56.79, 3.7)),
            Map.entry("浙江", focus("浙江", 73.90, 60.39, 3.9)),
            Map.entry("安徽", focus("安徽", 69.46, 57.26, 3.35)),
            Map.entry("福建", focus("福建", 72.54, 68.66, 3.55)),
            Map.entry("江西", focus("江西", 67.42, 63.49, 3.35)),
            Map.entry("山东", focus("山东", 69.11, 47.65, 3.05)),
            Map.entry("河南", focus("河南", 64.10, 51.45, 3.1)),
            Map.entry("湖北", focus("湖北", 65.11, 59.70, 3.05)),
            Map.entry("湖南", focus("湖南", 63.08, 64.39, 3.0)),
            Map.entry("广东", focus("广东", 63.56, 74.51, 2.9)),
            Map.entry("广西", focus("广西", 56.27, 75.13, 2.65)),
            Map.entry("海南", focus("海南", 59.19, 80.66, 4.0)),
            Map.entry("重庆", focus("重庆", 53.57, 61.73, 3.2)),
            Map.entry("四川", focus("四川", 49.87, 59.74, 2.65)),
            Map.entry("贵州", focus("贵州", 53.69, 67.52, 3.0)),
            Map.entry("云南", focus("云南", 47.87, 70.72, 2.55)),
            Map.entry("西藏", focus("西藏", 30.59, 61.58, 1.95)),
            Map.entry("陕西", focus("陕西", 57.13, 52.41, 3.0)),
            Map.entry("甘肃", focus("甘肃", 49.53, 48.85, 2.3)),
            Map.entry("青海", focus("青海", 46.47, 47.74, 2.15)),
            Map.entry("宁夏", focus("宁夏", 53.09, 44.03, 3.1)),
            Map.entry("新疆", focus("新疆", 25.38, 33.44, 1.85)),
            Map.entry("台湾", focus("台湾", 75.92, 70.73, 4.2)),
            Map.entry("香港", focus("香港", 64.91, 76.21, 4.8)),
            Map.entry("澳门", focus("澳门", 63.98, 76.38, 4.8)));

    private final TravelPostRepository postRepository;
    private final CurrentUserService currentUserService;
    private final ProvinceCatalogService provinceCatalogService;
    private final DestinationMapService destinationMapService;

    public MapController(
            TravelPostRepository postRepository,
            CurrentUserService currentUserService,
            ProvinceCatalogService provinceCatalogService,
            DestinationMapService destinationMapService) {
        this.postRepository = postRepository;
        this.currentUserService = currentUserService;
        this.provinceCatalogService = provinceCatalogService;
        this.destinationMapService = destinationMapService;
    }

    @GetMapping("/map")
    public String map(
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String mode,
            HttpSession session,
            Model model) {
        User currentUser = currentUserService.getCurrentUser(session);
        String requestedMode = resolveRequestedMode(mode, session);
        String selectedMode = normalizeMode(requestedMode, currentUser != null);
        boolean personalMode = MODE_PERSONAL.equals(selectedMode);
        boolean personalModeFallback = MODE_PERSONAL.equalsIgnoreCase(requestedMode) && currentUser == null;

        session.setAttribute(SESSION_MAP_MODE, selectedMode);

        List<TravelPost> sourcePosts = filterPostsByMode(
                postRepository.findAllByOrderByCreatedAtDesc(), currentUser, personalMode);
        String selectedProvince = normalizeSelectedProvince(province);

        List<TravelPost> provinceResolvedPosts = sourcePosts.stream()
                .filter(post -> resolveProvinceName(post).isPresent())
                .toList();
        List<TravelPost> unmappedPosts = sourcePosts.stream()
                .filter(post -> destinationMapService.resolvePoint(post).isEmpty())
                .toList();
        List<MarkerCandidate> mappablePosts = sourcePosts.stream()
                .map(this::toMarkerCandidate)
                .flatMap(Optional::stream)
                .toList();

        Map<String, Long> provinceCounts = mappablePosts.stream()
                .map(MarkerCandidate::province)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));

        List<MapMarkerView> markers = mappablePosts.stream()
                .filter(marker -> selectedProvince == null || selectedProvince.equals(marker.province()))
                .map(marker -> new MapMarkerView(
                        marker.post().getId(),
                        marker.post().getTitle(),
                        marker.post().getLocation(),
                        marker.post().getAuthor().getNickname(),
                        marker.province(),
                        marker.point().left(),
                        marker.point().top()))
                .toList();

        List<ProvinceRankingView> provinceRanking = provinceCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(6)
                .map(entry -> new ProvinceRankingView(entry.getKey(), entry.getValue()))
                .toList();

        model.addAttribute("mapMode", selectedMode);
        model.addAttribute("isPersonalMode", personalMode);
        model.addAttribute("personalModeAvailable", currentUser != null);
        model.addAttribute("personalModeFallback", personalModeFallback);
        model.addAttribute("mapOwnerLabel", personalMode && currentUser != null
                ? currentUser.getNickname() + "的足迹"
                : "全部用户足迹");
        model.addAttribute("markers", markers);
        model.addAttribute("unmappedPosts", unmappedPosts);
        model.addAttribute("provinceNames", provinceCatalogService.provinceNames());
        model.addAttribute("provinceCounts", provinceCounts);
        model.addAttribute("provinceRanking", provinceRanking);
        model.addAttribute("provinceFocuses", PROVINCE_FOCUSES);
        model.addAttribute("selectedProvince", selectedProvince);
        model.addAttribute("sourcePostCount", sourcePosts.size());
        model.addAttribute("coveredProvinceCount", provinceCounts.size());
        model.addAttribute("totalMappedPosts", markers.size());
        model.addAttribute("allMappedPosts", mappablePosts.size());
        model.addAttribute("resolvedProvincePosts", provinceResolvedPosts.size());
        return "map";
    }

    private Optional<MarkerCandidate> toMarkerCandidate(TravelPost post) {
        Optional<String> province = resolveProvinceName(post);
        Optional<DestinationMapService.MapPoint> point = destinationMapService.resolvePoint(post);
        if (province.isEmpty() || point.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new MarkerCandidate(post, province.get(), point.get()));
    }

    private String resolveRequestedMode(String mode, HttpSession session) {
        if (mode != null && !mode.isBlank()) {
            return mode.trim();
        }
        Object rememberedMode = session.getAttribute(SESSION_MAP_MODE);
        if (rememberedMode instanceof String remembered && !remembered.isBlank()) {
            return remembered;
        }
        return MODE_PUBLIC;
    }

    private List<TravelPost> filterPostsByMode(List<TravelPost> allPosts, User currentUser, boolean personalMode) {
        if (!personalMode || currentUser == null) {
            return allPosts;
        }
        return allPosts.stream()
                .filter(post -> post.getAuthor() != null && Objects.equals(post.getAuthor().getId(), currentUser.getId()))
                .toList();
    }

    private String normalizeMode(String mode, boolean loggedIn) {
        if (MODE_PERSONAL.equalsIgnoreCase(mode) && loggedIn) {
            return MODE_PERSONAL;
        }
        return MODE_PUBLIC;
    }

    private Optional<String> resolveProvinceName(TravelPost post) {
        return provinceCatalogService.resolveProvince(post.getProvince(), post.getLocation());
    }

    private String normalizeSelectedProvince(String province) {
        return provinceCatalogService.normalizeProvince(province).orElse(null);
    }

    private static ProvinceFocusView focus(String province, double left, double top, double scale) {
        return new ProvinceFocusView(province, left, top, scale);
    }

    private record MarkerCandidate(TravelPost post, String province, DestinationMapService.MapPoint point) {
    }

    public record ProvinceRankingView(String name, long count) {
    }

    public record MapMarkerView(
            Long postId,
            String title,
            String location,
            String author,
            String province,
            double left,
            double top) {
    }

    public record ProvinceFocusView(String province, double left, double top, double scale) {
    }
}

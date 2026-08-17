package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.TravelPostRepository;
import com.example.travelfootprint.service.ContentVisibilityService;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.DestinationMapService;
import com.example.travelfootprint.service.LocationNormalizationService;
import com.example.travelfootprint.service.ProvinceCatalogService;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private static final String SORT_LATEST = "latest";
    private static final String SORT_OLDEST = "oldest";
    private static final String SORT_TITLE = "title";
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
    private final ContentVisibilityService contentVisibilityService;
    private final LocationNormalizationService locationNormalizationService;

    public MapController(
            TravelPostRepository postRepository,
            CurrentUserService currentUserService,
            ProvinceCatalogService provinceCatalogService,
            DestinationMapService destinationMapService,
            ContentVisibilityService contentVisibilityService,
            LocationNormalizationService locationNormalizationService) {
        this.postRepository = postRepository;
        this.currentUserService = currentUserService;
        this.provinceCatalogService = provinceCatalogService;
        this.destinationMapService = destinationMapService;
        this.contentVisibilityService = contentVisibilityService;
        this.locationNormalizationService = locationNormalizationService;
    }

    @GetMapping("/map")
    public String map(
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean hasPhoto,
            @RequestParam(required = false) String sort,
            HttpSession session,
            Model model) {
        User currentUser = currentUserService.getCurrentUser(session);
        String requestedMode = resolveRequestedMode(mode, session);
        String selectedMode = normalizeMode(requestedMode, currentUser != null);
        boolean personalMode = MODE_PERSONAL.equals(selectedMode);
        boolean personalModeFallback = MODE_PERSONAL.equalsIgnoreCase(requestedMode) && currentUser == null;

        session.setAttribute(SESSION_MAP_MODE, selectedMode);

        List<TravelPost> allPosts = postRepository.findAllByOrderByCreatedAtDesc();
        List<TravelPost> sourcePosts = personalMode && currentUser != null
                ? allPosts.stream()
                        .filter(post -> post.getAuthor() != null && Objects.equals(post.getAuthor().getId(), currentUser.getId()))
                        .filter(post -> contentVisibilityService.canViewPost(currentUser, post))
                        .toList()
                : contentVisibilityService.approvedPosts(allPosts);
        List<Integer> availableYears = sourcePosts.stream()
                .map(TravelPost::getTravelDate)
                .filter(Objects::nonNull)
                .map(LocalDate::getYear)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
        List<String> availableCategories = sourcePosts.stream()
                .map(TravelPost::getCategory)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
        Integer selectedYear = availableYears.contains(year) ? year : null;
        String selectedCategory = category == null ? null : category.trim();
        if (selectedCategory == null || !availableCategories.contains(selectedCategory)) {
            selectedCategory = null;
        }
        String selectedKeyword = normalizeKeyword(q);
        String selectedSort = normalizeSort(sort);
        String finalSelectedCategory = selectedCategory;
        List<TravelPost> filteredPosts = sourcePosts.stream()
                .filter(post -> selectedYear == null
                        || (post.getTravelDate() != null && post.getTravelDate().getYear() == selectedYear))
                .filter(post -> finalSelectedCategory == null
                        || finalSelectedCategory.equals(post.getCategory()))
                .filter(post -> selectedKeyword == null || containsKeyword(post, selectedKeyword))
                .filter(post -> !hasPhoto || (post.getPhotoPath() != null && !post.getPhotoPath().isBlank()))
                .sorted(postComparator(selectedSort))
                .toList();
        String selectedProvince = normalizeSelectedProvince(province);

        List<TravelPost> provinceResolvedPosts = filteredPosts.stream()
                .filter(post -> resolveProvinceName(post).isPresent())
                .toList();
        List<TravelPost> unmappedPosts = filteredPosts.stream()
                .filter(post -> selectedProvince == null
                        || resolveProvinceName(post).filter(selectedProvince::equals).isPresent())
                .filter(post -> destinationMapService.resolvePoint(post).isEmpty())
                .toList();
        List<MarkerCandidate> mappablePosts = filteredPosts.stream()
                .map(post -> toMarkerCandidate(post, personalMode))
                .flatMap(Optional::stream)
                .toList();

        List<String> availableCities = mappablePosts.stream()
                .filter(marker -> selectedProvince != null && selectedProvince.equals(marker.province()))
                .map(MarkerCandidate::groupLabel)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted()
                .toList();
        String selectedCity = city == null ? null : city.trim();
        if (selectedCity == null || !availableCities.contains(selectedCity)) {
            selectedCity = null;
        }
        String finalSelectedCity = selectedCity;

        Map<String, Long> provinceCounts = mappablePosts.stream()
                .map(MarkerCandidate::province)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));

        List<MapMarkerView> markers = mappablePosts.stream()
                .filter(marker -> selectedProvince == null || selectedProvince.equals(marker.province()))
                .filter(marker -> finalSelectedCity == null || finalSelectedCity.equals(marker.groupLabel()))
                .map(marker -> new MapMarkerView(
                        marker.post().getId(),
                        marker.post().getTitle(),
                        marker.post().isApproximateLocation() && !personalMode
                                ? marker.province() + " · 具体位置已隐藏"
                                : locationNormalizationService.normalizeDisplayLocation(marker.post()),
                        marker.groupKey(),
                        marker.groupLabel(),
                        marker.post().getAuthor().getNickname(),
                        marker.province(),
                        marker.point().left(),
                        marker.point().top(),
                        marker.post().getCreatedAt(),
                        marker.post().getCategory(),
                        marker.post().getTravelDate(),
                        marker.post().getPhotoPath(),
                        marker.post().getVideoPath(),
                        excerpt(marker.post().getContent())))
                .toList();

        Map<String, List<MapMarkerView>> locationGroups = markers.stream()
                .collect(Collectors.groupingBy(
                        MapMarkerView::groupKey,
                        LinkedHashMap::new,
                        Collectors.toList()));
        long maximumLocationCount = locationGroups.values().stream()
                .mapToLong(List::size)
                .max()
                .orElse(1L);
        List<LocationHeatView> locationHeat = locationGroups.values().stream()
                .map(group -> {
                    MapMarkerView anchor = group.get(0);
                    long count = group.size();
                    double relativeStrength = (double) count / maximumLocationCount;
                    double intensity = 0.28 + relativeStrength * 0.72;
                    double diameter = Math.min(88.0, 34.0 + Math.sqrt(count) * 16.0);
                    return new LocationHeatView(
                            anchor.groupKey(),
                            anchor.province(),
                            anchor.groupLabel(),
                            count,
                            anchor.left(),
                            anchor.top(),
                            diameter,
                            intensity);
                })
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
        model.addAttribute("locationHeat", locationHeat);
        model.addAttribute("provinceRanking", provinceRanking);
        model.addAttribute("provinceFocuses", PROVINCE_FOCUSES);
        model.addAttribute("selectedProvince", selectedProvince);
        model.addAttribute("availableCities", availableCities);
        model.addAttribute("selectedCity", selectedCity);
        model.addAttribute("availableYears", availableYears);
        model.addAttribute("availableCategories", availableCategories);
        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute("selectedMapCategory", selectedCategory);
        model.addAttribute("selectedMapKeyword", selectedKeyword);
        model.addAttribute("selectedMapHasPhoto", hasPhoto);
        model.addAttribute("selectedMapSort", selectedSort);
        model.addAttribute("mapFiltersActive", selectedYear != null
                || selectedCategory != null
                || selectedProvince != null
                || selectedCity != null
                || selectedKeyword != null
                || hasPhoto
                || !SORT_LATEST.equals(selectedSort));
        model.addAttribute("sourcePostCount", sourcePosts.size());
        model.addAttribute("filteredPostCount", filteredPosts.size());
        model.addAttribute("coveredProvinceCount", provinceCounts.size());
        model.addAttribute("mappedLocationCount", markers.stream().map(MapMarkerView::groupKey).distinct().count());
        model.addAttribute("totalMappedPosts", markers.size());
        model.addAttribute("allMappedPosts", mappablePosts.size());
        model.addAttribute("resolvedProvincePosts", provinceResolvedPosts.size());
        return "map";
    }

    private Optional<MarkerCandidate> toMarkerCandidate(TravelPost post, boolean personalMode) {
        Optional<String> province = resolveProvinceName(post);
        if (post.isApproximateLocation() && !personalMode && province.isPresent()) {
            ProvinceFocusView focus = PROVINCE_FOCUSES.get(province.get());
            if (focus != null) {
                return Optional.of(new MarkerCandidate(
                        post,
                        province.get(),
                        new DestinationMapService.MapPoint(focus.left(), focus.top()),
                        province.get() + "|模糊位置",
                        province.get() + " · 位置已模糊"));
            }
        }
        Optional<DestinationMapService.MapPlacement> placement = destinationMapService.resolvePlacement(post);
        if (province.isEmpty() || placement.isEmpty()) {
            return Optional.empty();
        }
        String groupLabel = placement.get().groupLabel().isBlank()
                ? locationNormalizationService.normalizeDisplayLocation(post)
                : placement.get().groupLabel();
        String groupKey = province.get() + "|" + (placement.get().groupKey().isBlank() ? groupLabel : placement.get().groupKey());
        return Optional.of(new MarkerCandidate(post, province.get(), placement.get().point(), groupKey, groupLabel));
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

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String normalized = keyword.trim().replaceAll("\\s+", " ");
        return normalized.substring(0, Math.min(normalized.length(), 60));
    }

    private String normalizeSort(String sort) {
        if (SORT_OLDEST.equalsIgnoreCase(sort)) {
            return SORT_OLDEST;
        }
        if (SORT_TITLE.equalsIgnoreCase(sort)) {
            return SORT_TITLE;
        }
        return SORT_LATEST;
    }

    private boolean containsKeyword(TravelPost post, String keyword) {
        String author = post.getAuthor() == null ? "" : post.getAuthor().getNickname();
        String searchableText = String.join(" ",
                valueOrEmpty(post.getTitle()),
                valueOrEmpty(post.getLocation()),
                valueOrEmpty(post.getProvince()),
                valueOrEmpty(post.getCategory()),
                valueOrEmpty(post.getTags()),
                valueOrEmpty(post.getContent()),
                valueOrEmpty(author));
        return searchableText.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private Comparator<TravelPost> postComparator(String sort) {
        if (SORT_OLDEST.equals(sort)) {
            return Comparator.comparing(
                            TravelPost::getTravelDate,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(
                            TravelPost::getCreatedAt,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(TravelPost::getId);
        }
        if (SORT_TITLE.equals(sort)) {
            return Comparator.comparing(
                            (TravelPost post) -> valueOrEmpty(post.getTitle()),
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(TravelPost::getId);
        }
        return Comparator.comparing(
                        TravelPost::getTravelDate,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(
                        TravelPost::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(TravelPost::getId);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String excerpt(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.trim();
        return normalized.length() <= 90 ? normalized : normalized.substring(0, 90) + "...";
    }

    private static ProvinceFocusView focus(String province, double left, double top, double scale) {
        return new ProvinceFocusView(province, left, top, scale);
    }

    private record MarkerCandidate(
            TravelPost post,
            String province,
            DestinationMapService.MapPoint point,
            String groupKey,
            String groupLabel) {
    }

    public record ProvinceRankingView(String name, long count) {
    }

    public record MapMarkerView(
            Long postId,
            String title,
            String location,
            String groupKey,
            String groupLabel,
            String author,
            String province,
            double left,
            double top,
            LocalDateTime publishedAt,
            String category,
            LocalDate travelDate,
            String photoPath,
            String videoPath,
            String excerpt) {
    }

    public record ProvinceFocusView(String province, double left, double top, double scale) {
    }

    public record LocationHeatView(
            String groupKey,
            String province,
            String label,
            long count,
            double left,
            double top,
            double diameter,
            double intensity) {
    }
}

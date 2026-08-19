package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.TravelPostPhotoRepository;
import com.example.travelfootprint.repository.TravelPostRepository;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.ProvinceCatalogService;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TravelPassportController {

    private final TravelPostRepository postRepository;
    private final TravelPostPhotoRepository photoRepository;
    private final CurrentUserService currentUserService;
    private final ProvinceCatalogService provinceCatalogService;

    public TravelPassportController(
            TravelPostRepository postRepository,
            TravelPostPhotoRepository photoRepository,
            CurrentUserService currentUserService,
            ProvinceCatalogService provinceCatalogService) {
        this.postRepository = postRepository;
        this.photoRepository = photoRepository;
        this.currentUserService = currentUserService;
        this.provinceCatalogService = provinceCatalogService;
    }

    @GetMapping("/passport")
    public String passport(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "登录后才能查看你的旅行护照。");
            return "redirect:/login";
        }

        List<TravelPost> posts = postRepository.findByAuthorIdOrderByCreatedAtDesc(currentUser.getId()).stream()
                .sorted(Comparator.comparing(this::journeyDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TravelPost::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        Map<String, List<TravelPost>> provincePosts = posts.stream()
                .map(post -> Map.entry(resolveProvince(post), post))
                .filter(entry -> !entry.getKey().isBlank())
                .collect(Collectors.groupingBy(Map.Entry::getKey, LinkedHashMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        Map<String, Long> locationCounts = posts.stream()
                .map(TravelPost::getLocation)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));

        Map<Long, Long> albumCounts = posts.isEmpty() ? Map.of() : photoRepository
                .countByPostIds(posts.stream().map(TravelPost::getId).toList()).stream()
                .collect(Collectors.toMap(
                        TravelPostPhotoRepository.PostPhotoCount::getPostId,
                        TravelPostPhotoRepository.PostPhotoCount::getPhotoCount));
        long photoCount = posts.stream().mapToLong(post -> {
            long albumCount = albumCounts.getOrDefault(post.getId(), 0L);
            return Math.max(albumCount, post.getPhotoPath() == null || post.getPhotoPath().isBlank() ? 0 : 1);
        }).sum();
        long videoCount = posts.stream()
                .filter(post -> post.getVideoPath() != null && !post.getVideoPath().isBlank())
                .count();
        long travelDayCount = posts.stream().map(this::journeyDate).filter(date -> date != null).distinct().count();
        long travelYearCount = posts.stream().map(this::journeyDate).filter(date -> date != null)
                .map(LocalDate::getYear).distinct().count();
        long travelMonthCount = posts.stream().map(this::journeyDate).filter(date -> date != null)
                .map(LocalDate::getMonthValue).distinct().count();
        boolean revisited = locationCounts.values().stream().anyMatch(count -> count >= 2);

        List<PassportStampView> stamps = provinceCatalogService.provinceNames().stream()
                .map(province -> {
                    List<TravelPost> visits = provincePosts.getOrDefault(province, List.of());
                    LocalDate firstVisit = visits.stream().map(this::journeyDate).filter(date -> date != null)
                            .min(LocalDate::compareTo).orElse(null);
                    return new PassportStampView(province, !visits.isEmpty(), visits.size(), firstVisit);
                })
                .toList();

        List<PassportBadgeView> badges = new ArrayList<>();
        badges.add(badge("第一枚脚印", "发布第 1 条旅行足迹", posts.size(), 1, "01"));
        badges.add(badge("动态旅行家", "上传第 1 段旅行视频", videoCount, 1, "▶"));
        badges.add(badge("影像记录者", "累计记录 10 张旅行照片", photoCount, 10, "▧"));
        badges.add(badge("跨省探索者", "点亮 5 个省级区域", provincePosts.size(), 5, "✦"));
        badges.add(badge("足迹收藏家", "累计发布 10 条足迹", posts.size(), 10, "10"));
        badges.add(badge("四季行者", "在 4 个不同月份留下足迹", travelMonthCount, 4, "四"));
        badges.add(new PassportBadgeView("故地重游", "同一地点留下至少 2 次足迹", revisited,
                revisited ? 2 : 0, 2, "↻"));

        List<JourneyMilestoneView> milestones = posts.stream()
                .sorted(Comparator.comparing(this::journeyDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(TravelPost::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(post -> new JourneyMilestoneView(
                        post.getId(), post.getTitle(), post.getLocation(), resolveProvince(post), journeyDate(post),
                        post.getTravelDate() == null, post.getPhotoPath(),
                        post.getVideoPath() != null && !post.getVideoPath().isBlank()))
                .toList();

        model.addAttribute("passportUser", currentUser);
        model.addAttribute("passportNumber", "TF-%06d".formatted(currentUser.getId()));
        model.addAttribute("postCount", posts.size());
        model.addAttribute("travelDayCount", travelDayCount);
        model.addAttribute("travelYearCount", travelYearCount);
        model.addAttribute("provinceCount", provincePosts.size());
        model.addAttribute("photoCount", photoCount);
        model.addAttribute("videoCount", videoCount);
        model.addAttribute("stamps", stamps);
        model.addAttribute("badges", badges);
        model.addAttribute("earnedBadgeCount", badges.stream().filter(PassportBadgeView::earned).count());
        model.addAttribute("milestones", milestones);
        return "travel-passport";
    }

    private PassportBadgeView badge(String name, String description, long current, long target, String symbol) {
        return new PassportBadgeView(name, description, current >= target, Math.min(current, target), target, symbol);
    }

    private String resolveProvince(TravelPost post) {
        return provinceCatalogService.resolveProvince(post.getProvince(), post.getLocation()).orElse("");
    }

    private LocalDate journeyDate(TravelPost post) {
        if (post.getTravelDate() != null) {
            return post.getTravelDate();
        }
        return post.getCreatedAt() == null ? null : post.getCreatedAt().toLocalDate();
    }

    public record PassportStampView(String province, boolean visited, int visitCount, LocalDate firstVisitedOn) {
    }

    public record PassportBadgeView(
            String name, String description, boolean earned, long current, long target, String symbol) {
        public long progressPercent() {
            return target <= 0 ? 100 : Math.min(100, current * 100 / target);
        }
    }

    public record JourneyMilestoneView(
            Long postId,
            String title,
            String location,
            String province,
            LocalDate journeyDate,
            boolean dateEstimated,
            String photoPath,
            boolean hasVideo) {
    }
}

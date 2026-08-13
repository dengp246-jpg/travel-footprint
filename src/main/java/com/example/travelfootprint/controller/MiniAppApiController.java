package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.PostFavorite;
import com.example.travelfootprint.model.PostLike;
import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.TripPlan;
import com.example.travelfootprint.model.TripPlanStatus;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.CommentRepository;
import com.example.travelfootprint.repository.PostFavoriteRepository;
import com.example.travelfootprint.repository.PostLikeRepository;
import com.example.travelfootprint.repository.PostRatingRepository;
import com.example.travelfootprint.repository.TravelPostRepository;
import com.example.travelfootprint.repository.TravelExpenseRepository;
import com.example.travelfootprint.repository.TripPlanMemberRepository;
import com.example.travelfootprint.repository.TripPlanRepository;
import com.example.travelfootprint.repository.UserFollowRepository;
import com.example.travelfootprint.repository.UserRepository;
import com.example.travelfootprint.service.AppCatalogService;
import com.example.travelfootprint.service.ContentVisibilityService;
import com.example.travelfootprint.service.DestinationMapService;
import com.example.travelfootprint.service.FileStorageService;
import com.example.travelfootprint.service.LocationNormalizationService;
import com.example.travelfootprint.service.MiniAppTokenService;
import com.example.travelfootprint.service.ProvinceCatalogService;
import com.example.travelfootprint.service.TravelReportService;
import com.example.travelfootprint.service.TripPlanWorkspaceService;
import com.example.travelfootprint.service.ViewDataService;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/mini")
public class MiniAppApiController {

    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_LOCATION_LENGTH = 100;
    private static final int MAX_CONTENT_LENGTH = 4000;
    private static final int MAX_TAGS_LENGTH = 100;
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 72;

    private final UserRepository userRepository;
    private final TravelPostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository likeRepository;
    private final PostFavoriteRepository favoriteRepository;
    private final PostRatingRepository ratingRepository;
    private final UserFollowRepository followRepository;
    private final PasswordEncoder passwordEncoder;
    private final MiniAppTokenService miniAppTokenService;
    private final ProvinceCatalogService provinceCatalogService;
    private final FileStorageService fileStorageService;
    private final ViewDataService viewDataService;
    private final ContentVisibilityService contentVisibilityService;
    private final LocationNormalizationService locationNormalizationService;
    private final AppCatalogService appCatalogService;
    private final DestinationMapService destinationMapService;
    private final TripPlanRepository tripPlanRepository;
    private final TravelReportService travelReportService;
    private final TravelExpenseRepository expenseRepository;
    private final TripPlanMemberRepository planMemberRepository;
    private final TripPlanWorkspaceService tripPlanWorkspaceService;

    public MiniAppApiController(
            UserRepository userRepository,
            TravelPostRepository postRepository,
            CommentRepository commentRepository,
            PostLikeRepository likeRepository,
            PostFavoriteRepository favoriteRepository,
            PostRatingRepository ratingRepository,
            UserFollowRepository followRepository,
            PasswordEncoder passwordEncoder,
            MiniAppTokenService miniAppTokenService,
            ProvinceCatalogService provinceCatalogService,
            FileStorageService fileStorageService,
            ViewDataService viewDataService,
            ContentVisibilityService contentVisibilityService,
            LocationNormalizationService locationNormalizationService,
            AppCatalogService appCatalogService,
            DestinationMapService destinationMapService,
            TripPlanRepository tripPlanRepository,
            TravelReportService travelReportService,
            TravelExpenseRepository expenseRepository,
            TripPlanMemberRepository planMemberRepository,
            TripPlanWorkspaceService tripPlanWorkspaceService) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.likeRepository = likeRepository;
        this.favoriteRepository = favoriteRepository;
        this.ratingRepository = ratingRepository;
        this.followRepository = followRepository;
        this.passwordEncoder = passwordEncoder;
        this.miniAppTokenService = miniAppTokenService;
        this.provinceCatalogService = provinceCatalogService;
        this.fileStorageService = fileStorageService;
        this.viewDataService = viewDataService;
        this.contentVisibilityService = contentVisibilityService;
        this.locationNormalizationService = locationNormalizationService;
        this.appCatalogService = appCatalogService;
        this.destinationMapService = destinationMapService;
        this.tripPlanRepository = tripPlanRepository;
        this.travelReportService = travelReportService;
        this.expenseRepository = expenseRepository;
        this.planMemberRepository = planMemberRepository;
        this.tripPlanWorkspaceService = tripPlanWorkspaceService;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody MiniLoginRequest request) {
        if (request == null || isBlank(request.username()) || isBlank(request.password())) {
            return badRequest("请输入账号和密码。");
        }
        Optional<User> user = userRepository.findByUsername(request.username().trim())
                .filter(User::isEnabled)
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()));
        if (user.isEmpty()) {
            return unauthorized("用户名或密码不正确。");
        }
        String token = miniAppTokenService.issueToken(user.get());
        return ResponseEntity.ok(new MiniAuthResponse(token, toUserProfile(user.get())));
    }

    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody MiniRegisterRequest request) {
        if (request == null || isBlank(request.username()) || isBlank(request.nickname())
                || isBlank(request.password()) || isBlank(request.confirmPassword())) {
            return badRequest("用户名、昵称和密码不能为空。");
        }
        if (!request.password().equals(request.confirmPassword())) {
            return badRequest("两次输入的密码不一致。");
        }
        String username = request.username().trim();
        String nickname = request.nickname().trim();
        String bio = request.bio() == null ? "" : request.bio().trim();
        if (username.length() > 50 || nickname.length() > 50 || bio.length() > 500
                || username.chars().anyMatch(Character::isWhitespace)) {
            return badRequest("用户名或个人资料格式不正确。");
        }
        if (request.password().length() < MIN_PASSWORD_LENGTH
                || request.password().length() > MAX_PASSWORD_LENGTH) {
            return badRequest("密码长度应为 6 到 72 个字符。");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            return badRequest("该用户名已被注册。");
        }

        User user = new User();
        user.setUsername(username);
        user.setNickname(nickname);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setBio(bio);
        user.setEnabled(true);
        user.setAdmin(false);
        userRepository.save(user);

        String token = miniAppTokenService.issueToken(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MiniAuthResponse(token, toUserProfile(user)));
    }

    @GetMapping("/auth/me")
    public ResponseEntity<?> me(@RequestHeader(value = "X-Mini-Token", required = false) String token) {
        User user = resolveUser(token);
        if (user == null) {
            return unauthorized("当前未登录。");
        }
        return ResponseEntity.ok(toUserProfile(user));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Map<String, Boolean>> logout(
            @RequestHeader(value = "X-Mini-Token", required = false) String token) {
        miniAppTokenService.revoke(token);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/catalog/provinces")
    public List<String> provinces() {
        return provinceCatalogService.provinceNames();
    }

    @GetMapping("/catalog/categories")
    public List<String> categories() {
        return appCatalogService.categories();
    }

    @GetMapping("/posts")
    public ResponseEntity<?> posts(
            @RequestHeader(value = "X-Mini-Token", required = false) String token,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String province) {
        User currentUser = resolveUser(token);
        if (mine && currentUser == null) {
            return unauthorized("请先登录后再查看你的足迹。");
        }

        List<TravelPost> posts = mine
                ? postRepository.findByAuthorIdOrderByCreatedAtDesc(currentUser.getId())
                : postRepository.findAllByOrderByCreatedAtDesc();
        List<TravelPost> visiblePosts = mine
                ? contentVisibilityService.visiblePostsForProfile(posts, currentUser, currentUser)
                : contentVisibilityService.approvedPosts(posts);
        List<TravelPost> filteredPosts = visiblePosts.stream()
                .filter(post -> q == null || q.isBlank() || containsKeyword(post, q))
                .filter(post -> province == null || province.isBlank() || province.equals(post.getProvince()))
                .toList();

        return ResponseEntity.ok(toPostSummaries(filteredPosts, currentUser));
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<?> postDetail(
            @PathVariable Long id,
            @RequestHeader(value = "X-Mini-Token", required = false) String token) {
        User currentUser = resolveUser(token);
        return postRepository.findById(id)
                .<ResponseEntity<?>>map(post -> contentVisibilityService.canViewPost(currentUser, post)
                        ? ResponseEntity.ok(toPostDetail(post, currentUser))
                        : ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(new MiniErrorResponse("未找到这条足迹。")))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new MiniErrorResponse("未找到这条足迹。")));
    }

    @PostMapping(value = "/posts", consumes = {
            MediaType.MULTIPART_FORM_DATA_VALUE,
            MediaType.APPLICATION_FORM_URLENCODED_VALUE
    })
    public ResponseEntity<?> createPost(
            @RequestHeader(value = "X-Mini-Token", required = false) String token,
            @RequestParam String title,
            @RequestParam String location,
            @RequestParam String province,
            @RequestParam String content,
            @RequestParam String travelDate,
            @RequestParam String category,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) String latitude,
            @RequestParam(required = false) String longitude,
            @RequestParam(required = false) MultipartFile photo) {
        User currentUser = resolveUser(token);
        if (currentUser == null) {
            return unauthorized("请先登录后再发布足迹。");
        }

        Optional<String> normalizedProvince = provinceCatalogService.normalizeProvince(province);
        if (isBlank(title) || isBlank(location) || isBlank(content) || isBlank(category) || isBlank(travelDate)) {
            return badRequest("标题、景点、正文、分类和日期不能为空。");
        }
        if (normalizedProvince.isEmpty()) {
            return badRequest("请选择有效的省份。");
        }

        String normalizedTitle = title.trim();
        String normalizedLocation = location.trim();
        String normalizedContent = content.trim();
        String normalizedCategory = category.trim();
        String normalizedTags = tags == null ? "" : tags.trim();
        if (normalizedTitle.length() > MAX_TITLE_LENGTH
                || normalizedLocation.length() > MAX_LOCATION_LENGTH
                || normalizedContent.length() > MAX_CONTENT_LENGTH
                || normalizedTags.length() > MAX_TAGS_LENGTH) {
            return badRequest("内容超过长度限制：标题/地点最多 100 字，标签最多 100 字，正文最多 4000 字。");
        }
        if (!appCatalogService.categories().contains(normalizedCategory)) {
            return badRequest("请选择有效的足迹分类。");
        }

        TravelPost post = new TravelPost();
        post.setAuthor(currentUser);
        post.setTitle(normalizedTitle);
        post.setLocation(locationNormalizationService.normalizeDisplayLocation(
                normalizedProvince.get(), normalizedLocation));
        post.setProvince(normalizedProvince.get());
        post.setContent(normalizedContent);
        post.setCategory(normalizedCategory);
        post.setTags(normalizedTags);
        post.setReviewStatus(contentVisibilityService.defaultPostStatus(currentUser, false));

        try {
            post.setTravelDate(LocalDate.parse(travelDate.trim()));
        } catch (DateTimeParseException exception) {
            return badRequest("出行日期格式不正确。");
        }

        try {
            Double parsedLatitude = parseCoordinate(latitude, -90, 90);
            Double parsedLongitude = parseCoordinate(longitude, -180, 180);
            if ((parsedLatitude == null) != (parsedLongitude == null)) {
                return badRequest("地点经纬度需要同时提供。");
            }
            post.setLatitude(parsedLatitude);
            post.setLongitude(parsedLongitude);
            post.setApproximateLocation(parsedLatitude == null);
        } catch (IllegalArgumentException exception) {
            return badRequest(exception.getMessage());
        }

        try {
            String storedPhoto = fileStorageService.store(photo, "posts");
            if (storedPhoto != null) {
                post.setPhotoPath(storedPhoto);
            }
        } catch (IOException exception) {
            return badRequest(exception.getMessage());
        }

        postRepository.save(post);
        return ResponseEntity.status(HttpStatus.CREATED).body(toPostDetail(post, currentUser));
    }

    @PostMapping("/posts/{id}/like")
    public ResponseEntity<?> toggleLike(
            @PathVariable Long id,
            @RequestHeader(value = "X-Mini-Token", required = false) String token) {
        User currentUser = resolveUser(token);
        if (currentUser == null) {
            return unauthorized("登录后才能点赞互动。");
        }
        TravelPost post = postRepository.findById(id).orElse(null);
        if (post == null || !contentVisibilityService.isApproved(post)
                || !contentVisibilityService.canViewPost(currentUser, post)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MiniErrorResponse("该足迹当前不可点赞。"));
        }
        Optional<PostLike> existing = likeRepository.findByPostIdAndUserId(id, currentUser.getId());
        boolean active = existing.isEmpty();
        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
        } else {
            PostLike like = new PostLike();
            like.setPost(post);
            like.setUser(currentUser);
            likeRepository.save(like);
        }
        return ResponseEntity.ok(new MiniInteractionResponse(active, likeRepository.countByPostId(id)));
    }

    @PostMapping("/posts/{id}/favorite")
    public ResponseEntity<?> toggleFavorite(
            @PathVariable Long id,
            @RequestHeader(value = "X-Mini-Token", required = false) String token) {
        User currentUser = resolveUser(token);
        if (currentUser == null) {
            return unauthorized("登录后才能收藏足迹。");
        }
        TravelPost post = postRepository.findById(id).orElse(null);
        if (post == null || !contentVisibilityService.isApproved(post)
                || !contentVisibilityService.canViewPost(currentUser, post)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MiniErrorResponse("该足迹当前不可收藏。"));
        }
        Optional<PostFavorite> existing = favoriteRepository.findByPostIdAndUserId(id, currentUser.getId());
        boolean active = existing.isEmpty();
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
        } else {
            PostFavorite favorite = new PostFavorite();
            favorite.setPost(post);
            favorite.setUser(currentUser);
            favoriteRepository.save(favorite);
        }
        return ResponseEntity.ok(new MiniInteractionResponse(active, favoriteRepository.countByPostId(id)));
    }

    @GetMapping("/map/overview")
    public ResponseEntity<?> mapOverview(
            @RequestHeader(value = "X-Mini-Token", required = false) String token,
            @RequestParam(defaultValue = "false") boolean mine) {
        User currentUser = resolveUser(token);
        if (mine && currentUser == null) {
            return unauthorized("请先登录后再查看个人足迹分布。");
        }

        List<TravelPost> posts = mine
                ? postRepository.findByAuthorIdOrderByCreatedAtDesc(currentUser.getId())
                : postRepository.findAllByOrderByCreatedAtDesc();
        List<TravelPost> visiblePosts = mine
                ? contentVisibilityService.visiblePostsForProfile(posts, currentUser, currentUser)
                : contentVisibilityService.approvedPosts(posts);
        List<TravelPost> validPosts = visiblePosts.stream()
                .filter(post -> post.getProvince() != null && !post.getProvince().isBlank())
                .toList();

        List<MiniProvinceCount> provinces = validPosts.stream()
                .collect(Collectors.groupingBy(TravelPost::getProvince, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .map(entry -> new MiniProvinceCount(entry.getKey(), entry.getValue()))
                .toList();

        List<MiniMapPoint> points = validPosts.stream()
                .sorted(Comparator.comparing(
                                TravelPost::getCreatedAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TravelPost::getId))
                .flatMap(post -> destinationMapService.resolveCoordinates(post).stream()
                        .map(point -> new MiniMapPoint(
                                post.getId(),
                                post.getProvince(),
                                post.getLocation(),
                                post.getTitle(),
                                post.getTravelDate(),
                                post.getCreatedAt(),
                                point.latitude(),
                                point.longitude())))
                .toList();
        return ResponseEntity.ok(new MiniMapOverviewResponse(provinces, points, mine));
    }

    @GetMapping("/plans")
    public ResponseEntity<?> plans(
            @RequestHeader(value = "X-Mini-Token", required = false) String token) {
        User currentUser = resolveUser(token);
        if (currentUser == null) {
            return unauthorized("请先登录后再管理行程计划。");
        }
        return ResponseEntity.ok(tripPlanRepository.findByOwnerIdOrderByStartDateAscCreatedAtDesc(currentUser.getId())
                .stream()
                .map(this::toPlanSummary)
                .toList());
    }

    @PostMapping("/plans")
    public ResponseEntity<?> createPlan(
            @RequestHeader(value = "X-Mini-Token", required = false) String token,
            @RequestBody MiniPlanRequest request) {
        User currentUser = resolveUser(token);
        if (currentUser == null) {
            return unauthorized("请先登录后再创建行程计划。");
        }
        if (request == null || isBlank(request.title()) || isBlank(request.destination())) {
            return badRequest("计划标题和目的地不能为空。");
        }
        if (request.title().trim().length() > 100 || request.destination().trim().length() > 100
                || (request.notes() != null && request.notes().trim().length() > 1200)) {
            return badRequest("计划标题、目的地或备注超过长度限制。");
        }

        TripPlan plan = new TripPlan();
        plan.setOwner(currentUser);
        plan.setTitle(request.title().trim());
        plan.setDestination(request.destination().trim());
        plan.setNotes(request.notes() == null ? "" : request.notes().trim());
        try {
            plan.setStartDate(parseOptionalDate(request.startDate()));
            plan.setEndDate(parseOptionalDate(request.endDate()));
            if (plan.getStartDate() != null && plan.getEndDate() != null
                    && plan.getEndDate().isBefore(plan.getStartDate())) {
                return badRequest("结束日期不能早于开始日期。");
            }
            if (!isBlank(request.budget())) {
                BigDecimal budget = new BigDecimal(request.budget().trim());
                if (budget.signum() < 0) {
                    return badRequest("预算不能为负数。");
                }
                plan.setBudget(budget);
            }
            plan.setStatus(parsePlanStatus(request.status()));
        } catch (DateTimeParseException | NumberFormatException exception) {
            return badRequest("行程日期或预算格式不正确。");
        }
        tripPlanRepository.save(plan);
        return ResponseEntity.status(HttpStatus.CREATED).body(toPlanSummary(plan));
    }

    @DeleteMapping("/plans/{id}")
    public ResponseEntity<?> deletePlan(
            @PathVariable Long id,
            @RequestHeader(value = "X-Mini-Token", required = false) String token) {
        User currentUser = resolveUser(token);
        if (currentUser == null) {
            return unauthorized("请先登录后再操作行程计划。");
        }
        TripPlan plan = tripPlanRepository.findById(id).orElse(null);
        if (plan == null || !plan.getOwner().getId().equals(currentUser.getId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MiniErrorResponse("没有找到可删除的行程计划。"));
        }
        List<TravelPost> linkedPosts = postRepository.findByTripPlanIdOrderByTravelDateAscCreatedAtAsc(id);
        linkedPosts.forEach(post -> post.setTripPlan(null));
        postRepository.saveAll(linkedPosts);
        List<com.example.travelfootprint.model.TravelExpense> linkedExpenses = expenseRepository.findByTripPlanId(id);
        linkedExpenses.forEach(expense -> expense.setTripPlan(null));
        expenseRepository.saveAll(linkedExpenses);
        tripPlanWorkspaceService.cleanupBeforePlanDeletion(plan);
        planMemberRepository.deleteByTripPlanId(id);
        tripPlanRepository.delete(plan);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/reports")
    public ResponseEntity<?> report(
            @RequestHeader(value = "X-Mini-Token", required = false) String token,
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(required = false) String date) {
        User currentUser = resolveUser(token);
        if (currentUser == null) {
            return unauthorized("请先登录后再查看旅行报告。");
        }
        TravelReportService.ReportPeriod reportPeriod = TravelReportService.ReportPeriod.from(period);
        LocalDate anchorDate;
        try {
            anchorDate = isBlank(date) ? LocalDate.now() : LocalDate.parse(date.trim());
        } catch (DateTimeParseException exception) {
            anchorDate = LocalDate.now();
        }
        TravelReportService.TravelReport report = travelReportService.build(currentUser, reportPeriod, anchorDate);
        return ResponseEntity.ok(new MiniReportResponse(
                report.period().getValue(),
                report.period().getLabel(),
                report.title(),
                report.startDate(),
                report.endDate(),
                report.previousDate(),
                report.nextDate(),
                report.hasNext(),
                report.postCount(),
                report.locationCount(),
                report.provinceCount(),
                report.travelDays(),
                report.photoCount(),
                report.expenseCount(),
                report.expenseTotal(),
                report.coveragePercent(),
                report.insight(),
                report.activity(),
                report.categories(),
                report.provinces(),
                toPostSummaries(report.timeline(), currentUser)));
    }

    private MiniPlanSummary toPlanSummary(TripPlan plan) {
        List<TravelPost> posts = postRepository.findByTripPlanIdOrderByTravelDateAscCreatedAtAsc(plan.getId());
        long completedDays = posts.stream()
                .map(TravelPost::getTravelDate)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
        long plannedDays = 0;
        if (plan.getStartDate() != null && plan.getEndDate() != null
                && !plan.getEndDate().isBefore(plan.getStartDate())) {
            plannedDays = ChronoUnit.DAYS.between(plan.getStartDate(), plan.getEndDate()) + 1;
        }
        long denominator = plannedDays > 0 ? plannedDays : Math.max(1, completedDays);
        long progress = Math.min(100, Math.round(completedDays * 100.0 / denominator));
        if (plan.getStatus() == TripPlanStatus.FINISHED) {
            progress = 100;
        }
        return new MiniPlanSummary(
                plan.getId(),
                plan.getTitle(),
                plan.getDestination(),
                plan.getStartDate(),
                plan.getEndDate(),
                plan.getBudget(),
                plan.getStatus().name(),
                planStatusLabel(plan.getStatus()),
                plan.getNotes(),
                plan.getCreatedAt(),
                posts.size(),
                completedDays,
                plannedDays,
                progress);
    }

    private LocalDate parseOptionalDate(String value) {
        return isBlank(value) ? null : LocalDate.parse(value.trim());
    }

    private TripPlanStatus parsePlanStatus(String value) {
        if (isBlank(value)) {
            return TripPlanStatus.PLANNED;
        }
        try {
            return TripPlanStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return TripPlanStatus.PLANNED;
        }
    }

    private String planStatusLabel(TripPlanStatus status) {
        return switch (status) {
            case PLANNED -> "规划中";
            case BOOKED -> "已预订";
            case FINISHED -> "已完成";
        };
    }

    private Double parseCoordinate(String rawValue, double min, double max) {
        if (isBlank(rawValue)) {
            return null;
        }
        try {
            double value = Double.parseDouble(rawValue.trim());
            if (!Double.isFinite(value) || value < min || value > max) {
                throw new IllegalArgumentException("地点经纬度超出有效范围。");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("地点经纬度格式不正确。");
        }
    }

    private User resolveUser(String token) {
        return miniAppTokenService.findUser(token).orElse(null);
    }

    private ResponseEntity<MiniErrorResponse> unauthorized(String message) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MiniErrorResponse(message));
    }

    private ResponseEntity<MiniErrorResponse> badRequest(String message) {
        return ResponseEntity.badRequest().body(new MiniErrorResponse(message));
    }

    private List<MiniPostSummary> toPostSummaries(List<TravelPost> posts, User currentUser) {
        Map<Long, Long> likeCounts = viewDataService.likeCounts(posts);
        Map<Long, Long> commentCounts = viewDataService.commentCounts(posts);
        Map<Long, Long> favoriteCounts = viewDataService.favoriteCounts(posts);
        Map<Long, Double> ratingAverages = viewDataService.ratingAverages(posts);
        Set<Long> likedPostIds = viewDataService.likedPostIds(currentUser);
        Set<Long> favoritePostIds = viewDataService.favoritePostIds(currentUser);

        return posts.stream()
                .map(post -> new MiniPostSummary(
                        post.getId(),
                        post.getTitle(),
                        post.getLocation(),
                        post.getProvince(),
                        post.getCategory(),
                        post.getTravelDate(),
                        post.getCreatedAt(),
                        excerpt(post.getContent()),
                        post.getPhotoPath(),
                        toAuthorSnippet(post.getAuthor()),
                        likeCounts.getOrDefault(post.getId(), 0L),
                        commentCounts.getOrDefault(post.getId(), 0L),
                        favoriteCounts.getOrDefault(post.getId(), 0L),
                        ratingAverages.getOrDefault(post.getId(), 0.0),
                        likedPostIds.contains(post.getId()),
                        favoritePostIds.contains(post.getId())))
                .toList();
    }

    private MiniPostDetail toPostDetail(TravelPost post, User currentUser) {
        List<TravelPost> singlePost = List.of(post);
        return new MiniPostDetail(
                post.getId(),
                post.getTitle(),
                post.getLocation(),
                post.getProvince(),
                post.getCategory(),
                post.getTags(),
                post.getTravelDate(),
                post.getCreatedAt(),
                post.getContent(),
                post.getPhotoPath(),
                toAuthorSnippet(post.getAuthor()),
                viewDataService.approvedCommentCount(post.getId()),
                likeRepository.countByPostId(post.getId()),
                favoriteRepository.countByPostId(post.getId()),
                viewDataService.ratingAverages(singlePost).getOrDefault(post.getId(), 0.0),
                currentUser != null && likeRepository.existsByPostIdAndUserId(post.getId(), currentUser.getId()),
                currentUser != null && favoriteRepository.existsByPostIdAndUserId(post.getId(), currentUser.getId()));
    }

    private MiniUserProfile toUserProfile(User user) {
        long postCount = postRepository.findByAuthorIdOrderByCreatedAtDesc(user.getId()).size();
        long followingCount = followRepository.countByFollowerId(user.getId());
        long followerCount = followRepository.countByFollowingId(user.getId());
        return new MiniUserProfile(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getBio(),
                user.getAvatarPath(),
                user.getJoinedAt(),
                postCount,
                followingCount,
                followerCount);
    }

    private MiniAuthorSnippet toAuthorSnippet(User user) {
        return new MiniAuthorSnippet(user.getId(), user.getNickname(), user.getAvatarPath());
    }

    private String excerpt(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.trim();
        return normalized.length() <= 70 ? normalized : normalized.substring(0, 70) + "...";
    }

    private boolean containsKeyword(TravelPost post, String keyword) {
        String normalized = keyword.trim().toLowerCase();
        return post.getTitle().toLowerCase().contains(normalized)
                || post.getLocation().toLowerCase().contains(normalized)
                || post.getContent().toLowerCase().contains(normalized)
                || post.getAuthor().getNickname().toLowerCase().contains(normalized);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record MiniLoginRequest(String username, String password) {
    }

    private record MiniRegisterRequest(
            String username,
            String nickname,
            String password,
            String confirmPassword,
            String bio) {
    }

    private record MiniAuthResponse(String token, MiniUserProfile user) {
    }

    private record MiniErrorResponse(String message) {
    }

    private record MiniAuthorSnippet(Long id, String nickname, String avatarPath) {
    }

    private record MiniUserProfile(
            Long id,
            String username,
            String nickname,
            String bio,
            String avatarPath,
            java.time.LocalDateTime joinedAt,
            long postCount,
            long followingCount,
            long followerCount) {
    }

    private record MiniPostSummary(
            Long id,
            String title,
            String location,
            String province,
            String category,
            LocalDate travelDate,
            java.time.LocalDateTime createdAt,
            String excerpt,
            String photoPath,
            MiniAuthorSnippet author,
            long likeCount,
            long commentCount,
            long favoriteCount,
            double ratingAverage,
            boolean liked,
            boolean favorited) {
    }

    private record MiniPostDetail(
            Long id,
            String title,
            String location,
            String province,
            String category,
            String tags,
            LocalDate travelDate,
            java.time.LocalDateTime createdAt,
            String content,
            String photoPath,
            MiniAuthorSnippet author,
            long commentCount,
            long likeCount,
            long favoriteCount,
            double ratingAverage,
            boolean liked,
            boolean favorited) {
    }

    private record MiniInteractionResponse(boolean active, long count) {
    }

    private record MiniProvinceCount(String province, long count) {
    }

    private record MiniMapPoint(
            Long postId,
            String province,
            String location,
            String title,
            LocalDate travelDate,
            java.time.LocalDateTime createdAt,
            double latitude,
            double longitude) {
    }

    private record MiniMapOverviewResponse(
            List<MiniProvinceCount> provinces,
            List<MiniMapPoint> points,
            boolean routeEnabled) {
    }

    private record MiniPlanRequest(
            String title,
            String destination,
            String startDate,
            String endDate,
            String budget,
            String status,
            String notes) {
    }

    private record MiniPlanSummary(
            Long id,
            String title,
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal budget,
            String status,
            String statusLabel,
            String notes,
            java.time.LocalDateTime createdAt,
            long postCount,
            long completedDays,
            long plannedDays,
            long progress) {
    }

    private record MiniReportResponse(
            String period,
            String periodLabel,
            String title,
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
            List<TravelReportService.ActivityBucket> activity,
            List<TravelReportService.DistributionItem> categories,
            List<TravelReportService.DistributionItem> provinces,
            List<MiniPostSummary> timeline) {
    }
}

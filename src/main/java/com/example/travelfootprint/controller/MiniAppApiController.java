package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.CommentRepository;
import com.example.travelfootprint.repository.PostFavoriteRepository;
import com.example.travelfootprint.repository.PostLikeRepository;
import com.example.travelfootprint.repository.PostRatingRepository;
import com.example.travelfootprint.repository.TravelPostRepository;
import com.example.travelfootprint.repository.UserFollowRepository;
import com.example.travelfootprint.repository.UserRepository;
import com.example.travelfootprint.service.FileStorageService;
import com.example.travelfootprint.service.MiniAppTokenService;
import com.example.travelfootprint.service.ProvinceCatalogService;
import com.example.travelfootprint.service.ViewDataService;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
            ViewDataService viewDataService) {
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
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody MiniLoginRequest request) {
        if (request == null || isBlank(request.username()) || isBlank(request.password())) {
            return badRequest("请输入账号和密码。");
        }
        Optional<User> user = userRepository.findByUsername(request.username().trim())
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
        if (userRepository.findByUsername(username).isPresent()) {
            return badRequest("该用户名已被注册。");
        }

        User user = new User();
        user.setUsername(username);
        user.setNickname(request.nickname().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setBio(request.bio() == null ? "" : request.bio().trim());
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
        List<TravelPost> filteredPosts = posts.stream()
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
                .<ResponseEntity<?>>map(post -> ResponseEntity.ok(toPostDetail(post, currentUser)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new MiniErrorResponse("未找到这条足迹。")));
    }

    @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createPost(
            @RequestHeader(value = "X-Mini-Token", required = false) String token,
            @RequestParam String title,
            @RequestParam String location,
            @RequestParam String province,
            @RequestParam String content,
            @RequestParam String travelDate,
            @RequestParam String category,
            @RequestParam(required = false) String tags,
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

        TravelPost post = new TravelPost();
        post.setAuthor(currentUser);
        post.setTitle(title.trim());
        post.setLocation(location.trim());
        post.setProvince(normalizedProvince.get());
        post.setContent(content.trim());
        post.setCategory(category.trim());
        post.setTags(tags == null ? "" : tags.trim());

        try {
            post.setTravelDate(LocalDate.parse(travelDate.trim()));
        } catch (DateTimeParseException exception) {
            return badRequest("出行日期格式不正确。");
        }

        try {
            String storedPhoto = fileStorageService.store(photo, "posts");
            if (storedPhoto != null) {
                post.setPhotoPath(storedPhoto);
            }
        } catch (IOException exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MiniErrorResponse("图片上传失败，请稍后再试。"));
        }

        postRepository.save(post);
        return ResponseEntity.status(HttpStatus.CREATED).body(toPostDetail(post, currentUser));
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
        List<TravelPost> validPosts = posts.stream()
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
                .map(post -> new MiniMapPoint(post.getId(), post.getProvince(), post.getLocation(), post.getTitle()))
                .toList();
        return ResponseEntity.ok(new MiniMapOverviewResponse(provinces, points));
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
                commentRepository.countByPostId(post.getId()),
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

    private record MiniProvinceCount(String province, long count) {
    }

    private record MiniMapPoint(Long postId, String province, String location, String title) {
    }

    private record MiniMapOverviewResponse(List<MiniProvinceCount> provinces, List<MiniMapPoint> points) {
    }
}

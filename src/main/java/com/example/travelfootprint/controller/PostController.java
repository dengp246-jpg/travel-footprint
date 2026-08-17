package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.Comment;
import com.example.travelfootprint.model.NotificationType;
import com.example.travelfootprint.model.PostFavorite;
import com.example.travelfootprint.model.PostLike;
import com.example.travelfootprint.model.PostRating;
import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.TripPlan;
import com.example.travelfootprint.model.PostVisibility;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.CommentRepository;
import com.example.travelfootprint.repository.PostFavoriteRepository;
import com.example.travelfootprint.repository.PostLikeRepository;
import com.example.travelfootprint.repository.PostRatingRepository;
import com.example.travelfootprint.repository.RecommendationDismissalRepository;
import com.example.travelfootprint.repository.TravelPostRepository;
import com.example.travelfootprint.repository.TravelExpenseRepository;
import com.example.travelfootprint.repository.TripPlanRepository;
import com.example.travelfootprint.service.AppCatalogService;
import com.example.travelfootprint.service.ContentVisibilityService;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.DestinationMapService;
import com.example.travelfootprint.service.LocationNormalizationService;
import com.example.travelfootprint.service.NotificationService;
import com.example.travelfootprint.service.ProvinceCatalogService;
import com.example.travelfootprint.service.PostPhotoService;
import com.example.travelfootprint.service.PostVideoService;
import com.example.travelfootprint.service.TripPlanAccessService;
import com.example.travelfootprint.service.ViewDataService;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PostController {

    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_LOCATION_LENGTH = 100;
    private static final int MAX_CONTENT_LENGTH = 4000;
    private static final int MAX_TAGS_LENGTH = 100;
    private static final int MAX_COMMENT_LENGTH = 1000;

    private final TravelPostRepository postRepository;
    private final TravelExpenseRepository expenseRepository;
    private final TripPlanRepository tripPlanRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository likeRepository;
    private final PostFavoriteRepository favoriteRepository;
    private final PostRatingRepository ratingRepository;
    private final RecommendationDismissalRepository recommendationDismissalRepository;
    private final CurrentUserService currentUserService;
    private final PostPhotoService postPhotoService;
    private final PostVideoService postVideoService;
    private final NotificationService notificationService;
    private final ViewDataService viewDataService;
    private final ProvinceCatalogService provinceCatalogService;
    private final ContentVisibilityService contentVisibilityService;
    private final LocationNormalizationService locationNormalizationService;
    private final AppCatalogService appCatalogService;
    private final DestinationMapService destinationMapService;
    private final TripPlanAccessService planAccessService;

    public PostController(
            TravelPostRepository postRepository,
            TravelExpenseRepository expenseRepository,
            TripPlanRepository tripPlanRepository,
            CommentRepository commentRepository,
            PostLikeRepository likeRepository,
            PostFavoriteRepository favoriteRepository,
            PostRatingRepository ratingRepository,
            RecommendationDismissalRepository recommendationDismissalRepository,
            CurrentUserService currentUserService,
            PostPhotoService postPhotoService,
            PostVideoService postVideoService,
            NotificationService notificationService,
            ViewDataService viewDataService,
            ProvinceCatalogService provinceCatalogService,
            ContentVisibilityService contentVisibilityService,
            LocationNormalizationService locationNormalizationService,
            AppCatalogService appCatalogService,
            DestinationMapService destinationMapService,
            TripPlanAccessService planAccessService) {
        this.postRepository = postRepository;
        this.expenseRepository = expenseRepository;
        this.tripPlanRepository = tripPlanRepository;
        this.commentRepository = commentRepository;
        this.likeRepository = likeRepository;
        this.favoriteRepository = favoriteRepository;
        this.ratingRepository = ratingRepository;
        this.recommendationDismissalRepository = recommendationDismissalRepository;
        this.currentUserService = currentUserService;
        this.postPhotoService = postPhotoService;
        this.postVideoService = postVideoService;
        this.notificationService = notificationService;
        this.viewDataService = viewDataService;
        this.provinceCatalogService = provinceCatalogService;
        this.contentVisibilityService = contentVisibilityService;
        this.locationNormalizationService = locationNormalizationService;
        this.appCatalogService = appCatalogService;
        this.destinationMapService = destinationMapService;
        this.planAccessService = planAccessService;
    }

    @GetMapping("/posts/new")
    public String newPostPage(
            @RequestParam(required = false) Long planId,
            HttpSession session,
            RedirectAttributes redirectAttributes,
            Model model) {
        if (!currentUserService.isLoggedIn(session)) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先登录，再发布旅游足迹。");
            return "redirect:/login";
        }
        TravelPost formPost = new TravelPost();
        User currentUser = currentUserService.getCurrentUser(session);
        formPost.setTripPlan(ownedTripPlan(planId, currentUser));
        model.addAttribute("formPost", formPost);
        model.addAttribute("editing", false);
        model.addAttribute("locationSuggestions", destinationMapService.locationSuggestions());
        model.addAttribute("availablePlans", planAccessService.visiblePlans(currentUser));
        model.addAttribute("postPhotos", List.of());
        model.addAttribute("postVisibilities", PostVisibility.values());
        return "post-form";
    }

    @PostMapping("/posts")
    public String createPost(
            @RequestParam String title,
            @RequestParam String location,
            @RequestParam String province,
            @RequestParam String content,
            @RequestParam String travelDate,
            @RequestParam String category,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) String latitude,
            @RequestParam(required = false) String longitude,
            @RequestParam(required = false) List<MultipartFile> photos,
            @RequestParam(required = false) MultipartFile video,
            @RequestParam(required = false) Integer coverPhotoIndex,
            @RequestParam(required = false) Long tripPlanId,
            @RequestParam(defaultValue = "PUBLIC") PostVisibility visibility,
            @RequestParam(defaultValue = "false") boolean approximateLocation,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先登录，再发布旅游足迹。");
            return "redirect:/login";
        }

        TravelPost post = new TravelPost();
        if (!fillPost(post, title, location, province, content, travelDate, category, tags, latitude, longitude,
                redirectAttributes)) {
            return "redirect:/posts/new";
        }
        TripPlan tripPlan = ownedTripPlan(tripPlanId, currentUser);
        if (tripPlanId != null && tripPlan == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "只能关联你拥有或已加入的行程计划。");
            return "redirect:/posts/new";
        }
        post.setAuthor(currentUser);
        post.setTripPlan(tripPlan);
        post.setVisibility(visibility);
        post.setApproximateLocation(approximateLocation);
        post.setReviewStatus(contentVisibilityService.defaultPostStatus(currentUser, false));
        postRepository.save(post);
        try {
            postVideoService.updateVideo(post, video, false);
            postPhotoService.addPhotos(post, photos, coverPhotoIndex);
        } catch (IOException exception) {
            postPhotoService.deleteByPostId(post.getId());
            postVideoService.deleteVideo(post);
            postRepository.delete(post);
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/posts/new";
        }
        redirectAttributes.addFlashAttribute(
                "successMessage",
                contentVisibilityService.isApproved(post)
                        ? "足迹发布成功，地图和搜索模块都已同步更新。"
                        : "足迹已提交，等待管理员审核通过后会出现在首页和地图中。");
        return "redirect:/posts/" + post.getId();
    }

    @GetMapping("/posts/{id}")
    public String postDetail(
            @PathVariable Long id,
            Model model,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        TravelPost post = postRepository.findById(id).orElseThrow();
        User currentUser = currentUserService.getCurrentUser(session);
        if (!contentVisibilityService.canViewPost(currentUser, post)) {
            redirectAttributes.addFlashAttribute("errorMessage", "这条足迹暂时不可查看。");
            return "redirect:/";
        }
        List<Comment> rootComments = viewDataService.approvedRootComments(id);

        model.addAttribute("post", post);
        model.addAttribute("comments", rootComments);
        model.addAttribute("replyMap", viewDataService.replyMap(id));
        model.addAttribute("likeCount", likeRepository.countByPostId(id));
        model.addAttribute("favoriteCount", favoriteRepository.countByPostId(id));
        model.addAttribute("commentCount", viewDataService.approvedCommentCount(id));
        model.addAttribute("ratingCount", ratingRepository.countByPostId(id));
        model.addAttribute("ratingAverage", viewDataService.ratingAverages(List.of(post)).getOrDefault(id, 0.0));
        model.addAttribute("liked", currentUser != null && likeRepository.existsByPostIdAndUserId(id, currentUser.getId()));
        model.addAttribute("favorited", currentUser != null && favoriteRepository.existsByPostIdAndUserId(id, currentUser.getId()));
        model.addAttribute("myRating", currentUser == null ? 0
                : ratingRepository.findByPostIdAndUserId(id, currentUser.getId()).map(PostRating::getScore).orElse(0));
        model.addAttribute("ownedByCurrentUser",
                currentUser != null && currentUser.getId().equals(post.getAuthor().getId()));
        model.addAttribute("postApproved", contentVisibilityService.isApproved(post));
        model.addAttribute("showModerationStatus",
                currentUser != null
                        && (currentUser.getId().equals(post.getAuthor().getId())
                        || contentVisibilityService.isAdmin(currentUser)));
        model.addAttribute("postPhotos", postPhotoService.gallery(post));
        model.addAttribute("displayLocation", post.isApproximateLocation() && (currentUser == null
                || !currentUser.getId().equals(post.getAuthor().getId()))
                ? post.getProvince() + " · 具体位置已隐藏" : post.getLocation());
        return "post-detail";
    }

    @GetMapping("/posts/{id}/edit")
    public String editPostPage(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes, Model model) {
        TravelPost post = postRepository.findById(id).orElseThrow();
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null || !currentUser.getId().equals(post.getAuthor().getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "只有发布者本人才能编辑这条足迹。");
            return "redirect:/posts/" + id;
        }

        model.addAttribute("formPost", post);
        model.addAttribute("editing", true);
        model.addAttribute("locationSuggestions", destinationMapService.locationSuggestions());
        model.addAttribute("availablePlans", planAccessService.visiblePlans(currentUser));
        model.addAttribute("postPhotos", postPhotoService.gallery(post));
        model.addAttribute("postVisibilities", PostVisibility.values());
        return "post-form";
    }

    @PostMapping("/posts/{id}/edit")
    public String updatePost(
            @PathVariable Long id,
            @RequestParam String title,
            @RequestParam String location,
            @RequestParam String province,
            @RequestParam String content,
            @RequestParam String travelDate,
            @RequestParam String category,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) String latitude,
            @RequestParam(required = false) String longitude,
            @RequestParam(required = false) List<MultipartFile> photos,
            @RequestParam(required = false) MultipartFile video,
            @RequestParam(defaultValue = "false") boolean removeVideo,
            @RequestParam(required = false) Integer coverPhotoIndex,
            @RequestParam(required = false) Long tripPlanId,
            @RequestParam(defaultValue = "PUBLIC") PostVisibility visibility,
            @RequestParam(defaultValue = "false") boolean approximateLocation,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        TravelPost post = postRepository.findById(id).orElseThrow();
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null || !currentUser.getId().equals(post.getAuthor().getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "只有发布者本人才能编辑这条足迹。");
            return "redirect:/posts/" + id;
        }

        if (!fillPost(post, title, location, province, content, travelDate, category, tags, latitude, longitude,
                redirectAttributes)) {
            return "redirect:/posts/" + id + "/edit";
        }
        TripPlan tripPlan = ownedTripPlan(tripPlanId, currentUser);
        if (tripPlanId != null && tripPlan == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "只能关联你拥有或已加入的行程计划。");
            return "redirect:/posts/" + id + "/edit";
        }
        post.setTripPlan(tripPlan);
        post.setVisibility(visibility);
        post.setApproximateLocation(approximateLocation);
        post.setReviewStatus(contentVisibilityService.defaultPostStatus(currentUser, false));
        postRepository.save(post);
        try {
            postPhotoService.addPhotos(post, photos, coverPhotoIndex);
            postVideoService.updateVideo(post, video, removeVideo);
        } catch (IOException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/posts/" + id + "/edit";
        }
        redirectAttributes.addFlashAttribute(
                "successMessage",
                contentVisibilityService.isApproved(post)
                        ? "足迹内容已更新。"
                        : "修改已保存，重新审核通过后会恢复公开展示。");
        return "redirect:/posts/" + id;
    }

    @PostMapping("/posts/{id}/delete")
    public String deletePost(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        TravelPost post = postRepository.findById(id).orElseThrow();
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null || !currentUser.getId().equals(post.getAuthor().getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "只有发布者本人才能删除这条足迹。");
            return "redirect:/posts/" + id;
        }

        recommendationDismissalRepository.deleteByPostId(id);
        favoriteRepository.deleteByPostId(id);
        likeRepository.deleteByPostId(id);
        ratingRepository.deleteByPostId(id);
        commentRepository.deleteByPostId(id);
        List<com.example.travelfootprint.model.TravelExpense> linkedExpenses = expenseRepository.findByTravelPostId(id);
        linkedExpenses.forEach(expense -> expense.setTravelPost(null));
        expenseRepository.saveAll(linkedExpenses);
        postPhotoService.deleteByPostId(id);
        postVideoService.deleteVideo(post);
        postRepository.delete(post);
        redirectAttributes.addFlashAttribute("successMessage", "足迹已删除。");
        return "redirect:/me";
    }

    @PostMapping("/posts/{id}/photos/{photoId}/cover")
    public String setPhotoCover(
            @PathVariable Long id,
            @PathVariable Long photoId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        TravelPost post = postRepository.findById(id).orElseThrow();
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null || !currentUser.getId().equals(post.getAuthor().getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "只有发布者本人才能调整相册封面。");
            return "redirect:/posts/" + id;
        }
        if (!postPhotoService.setCover(post, photoId)) {
            redirectAttributes.addFlashAttribute("errorMessage", "没有找到这张相册照片。");
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "相册封面已更新。");
        }
        return "redirect:/posts/" + id;
    }

    @PostMapping("/posts/{id}/comments")
    public String addComment(
            @PathVariable Long id,
            @RequestParam String content,
            @RequestParam(required = false) Long parentId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "登录后才能发表评论。");
            return "redirect:/login";
        }
        if (content.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "评论内容不能为空。");
            return "redirect:/posts/" + id;
        }
        if (content.trim().length() > MAX_COMMENT_LENGTH) {
            redirectAttributes.addFlashAttribute("errorMessage", "评论不能超过 " + MAX_COMMENT_LENGTH + " 个字符。");
            return "redirect:/posts/" + id;
        }

        TravelPost post = postRepository.findById(id).orElseThrow();
        if (!contentVisibilityService.isApproved(post) || !contentVisibilityService.canViewPost(currentUser, post)) {
            redirectAttributes.addFlashAttribute("errorMessage", "这条足迹当前不可评论。");
            return "redirect:/posts/" + id;
        }
        Comment comment = new Comment();
        comment.setPost(post);
        comment.setAuthor(currentUser);
        comment.setContent(content.trim());
        comment.setReviewStatus(contentVisibilityService.defaultCommentStatus(currentUser));
        if (parentId != null) {
            Optional<Comment> parentComment = commentRepository.findById(parentId);
            if (parentComment.isEmpty()
                    || !parentComment.get().getPost().getId().equals(post.getId())
                    || !contentVisibilityService.canViewComment(currentUser, parentComment.get())) {
                redirectAttributes.addFlashAttribute("errorMessage", "要回复的评论不存在或暂不可见。");
                return "redirect:/posts/" + id;
            }
            comment.setParentComment(parentComment.get());
        }
        commentRepository.save(comment);

        if (contentVisibilityService.isApproved(comment)) {
            notifyCommentApproved(comment, post, currentUser, id);
        }

        redirectAttributes.addFlashAttribute(
                "successMessage",
                contentVisibilityService.isApproved(comment)
                        ? (parentId == null ? "评论发布成功。" : "回复已发送。")
                        : "评论已提交，等待管理员审核。");
        return "redirect:/posts/" + id;
    }

    @PostMapping("/posts/{id}/like")
    public String toggleLike(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "登录后才能点赞互动。");
            return "redirect:/login";
        }

        TravelPost post = postRepository.findById(id).orElseThrow();
        if (!contentVisibilityService.isApproved(post) || !contentVisibilityService.canViewPost(currentUser, post)) {
            redirectAttributes.addFlashAttribute("errorMessage", "该足迹当前不可参与点赞。");
            return "redirect:/posts/" + id;
        }
        likeRepository.findByPostIdAndUserId(id, currentUser.getId()).ifPresentOrElse(
                likeRepository::delete,
                () -> {
                    PostLike like = new PostLike();
                    like.setPost(post);
                    like.setUser(currentUser);
                    likeRepository.save(like);
                    notificationService.notify(
                            post.getAuthor(),
                            currentUser,
                            NotificationType.LIKE,
                            currentUser.getNickname() + " 点赞了你的足迹",
                            "/posts/" + id);
                });
        return "redirect:/posts/" + id;
    }

    @PostMapping("/posts/{id}/favorite")
    public String toggleFavorite(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "登录后才能收藏景点。");
            return "redirect:/login";
        }

        TravelPost post = postRepository.findById(id).orElseThrow();
        if (!contentVisibilityService.isApproved(post) || !contentVisibilityService.canViewPost(currentUser, post)) {
            redirectAttributes.addFlashAttribute("errorMessage", "该足迹当前不可收藏。");
            return "redirect:/posts/" + id;
        }
        favoriteRepository.findByPostIdAndUserId(id, currentUser.getId()).ifPresentOrElse(
                favoriteRepository::delete,
                () -> {
                    PostFavorite favorite = new PostFavorite();
                    favorite.setPost(post);
                    favorite.setUser(currentUser);
                    favoriteRepository.save(favorite);
                    notificationService.notify(
                            post.getAuthor(),
                            currentUser,
                            NotificationType.FAVORITE,
                            currentUser.getNickname() + " 收藏了你的景点足迹",
                            "/posts/" + id);
                });
        return "redirect:/posts/" + id;
    }

    @PostMapping("/posts/{id}/rating")
    public String ratePost(
            @PathVariable Long id,
            @RequestParam Integer score,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "登录后才能评分。");
            return "redirect:/login";
        }
        if (score == null || score < 1 || score > 5) {
            redirectAttributes.addFlashAttribute("errorMessage", "评分必须在 1 到 5 分之间。");
            return "redirect:/posts/" + id;
        }

        TravelPost post = postRepository.findById(id).orElseThrow();
        if (!contentVisibilityService.isApproved(post) || !contentVisibilityService.canViewPost(currentUser, post)) {
            redirectAttributes.addFlashAttribute("errorMessage", "该足迹当前不可评分。");
            return "redirect:/posts/" + id;
        }
        PostRating rating = ratingRepository.findByPostIdAndUserId(id, currentUser.getId()).orElseGet(() -> {
            PostRating newRating = new PostRating();
            newRating.setPost(post);
            newRating.setUser(currentUser);
            return newRating;
        });
        rating.setScore(score);
        ratingRepository.save(rating);
        notificationService.notify(
                post.getAuthor(),
                currentUser,
                NotificationType.RATING,
                currentUser.getNickname() + " 给你的景点足迹打了 " + score + " 分",
                "/posts/" + id);
        redirectAttributes.addFlashAttribute("successMessage", "评分已提交。");
        return "redirect:/posts/" + id;
    }

    private boolean fillPost(
            TravelPost post,
            String title,
            String location,
            String province,
            String content,
            String travelDate,
            String category,
            String tags,
            String latitude,
            String longitude,
            RedirectAttributes redirectAttributes) {
        if (title == null || location == null || province == null || content == null || travelDate == null
                || category == null || title.isBlank() || location.isBlank() || province.isBlank()
                || content.isBlank() || travelDate.isBlank() || category.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "标题、地点、省份、分类、日期和正文都不能为空。");
            return false;
        }

        String normalizedTitle = title.trim();
        String normalizedLocationInput = location.trim();
        String normalizedContent = content.trim();
        String normalizedCategory = category.trim();
        String normalizedTags = tags == null ? "" : tags.trim();
        if (normalizedTitle.length() > MAX_TITLE_LENGTH
                || normalizedLocationInput.length() > MAX_LOCATION_LENGTH
                || normalizedContent.length() > MAX_CONTENT_LENGTH
                || normalizedTags.length() > MAX_TAGS_LENGTH) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "内容超过长度限制：标题/地点最多 100 字，标签最多 100 字，正文最多 4000 字。");
            return false;
        }
        if (!appCatalogService.categories().contains(normalizedCategory)) {
            redirectAttributes.addFlashAttribute("errorMessage", "请选择有效的足迹分类。");
            return false;
        }

        Optional<String> normalizedProvince = provinceCatalogService.resolveProvince(province, location);
        if (normalizedProvince.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "请选择有效的省份后再发布。");
            return false;
        }

        post.setTitle(normalizedTitle);
        post.setLocation(locationNormalizationService.normalizeDisplayLocation(
                normalizedProvince.get(), normalizedLocationInput));
        post.setProvince(normalizedProvince.get());
        post.setContent(normalizedContent);
        post.setCategory(normalizedCategory);
        post.setTags(normalizedTags);

        try {
            post.setTravelDate(LocalDate.parse(travelDate.trim()));
        } catch (DateTimeParseException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", "出行日期格式不正确。");
            return false;
        }

        try {
            post.setLatitude(parseCoordinate(latitude, -90, 90));
            post.setLongitude(parseCoordinate(longitude, -180, 180));
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return false;
        }

        return true;
    }

    private TripPlan ownedTripPlan(Long tripPlanId, User currentUser) {
        if (tripPlanId == null) {
            return null;
        }
        return tripPlanRepository.findById(tripPlanId)
                .filter(plan -> planAccessService.canEdit(plan, currentUser))
                .orElse(null);
    }

    private void notifyCommentApproved(Comment comment, TravelPost post, User currentUser, Long postId) {
        if (comment.getParentComment() != null) {
            notificationService.notify(
                    comment.getParentComment().getAuthor(),
                    currentUser,
                    NotificationType.REPLY,
                    currentUser.getNickname() + " 回复了你的评论",
                    "/posts/" + postId);
            return;
        }

        notificationService.notify(
                post.getAuthor(),
                currentUser,
                NotificationType.COMMENT,
                currentUser.getNickname() + " 评论了你的足迹",
                "/posts/" + postId);
    }

    private Double parseCoordinate(String rawValue, double min, double max) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            double value = Double.parseDouble(rawValue.trim());
            if (value < min || value > max) {
                throw new IllegalArgumentException("地图坐标超出允许范围。");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("地图坐标格式不正确。");
        }
    }
}

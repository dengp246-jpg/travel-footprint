package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.Comment;
import com.example.travelfootprint.model.NotificationType;
import com.example.travelfootprint.model.PostFavorite;
import com.example.travelfootprint.model.PostLike;
import com.example.travelfootprint.model.PostRating;
import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.CommentRepository;
import com.example.travelfootprint.repository.PostFavoriteRepository;
import com.example.travelfootprint.repository.PostLikeRepository;
import com.example.travelfootprint.repository.PostRatingRepository;
import com.example.travelfootprint.repository.TravelPostRepository;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.FileStorageService;
import com.example.travelfootprint.service.NotificationService;
import com.example.travelfootprint.service.ProvinceCatalogService;
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

    private final TravelPostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository likeRepository;
    private final PostFavoriteRepository favoriteRepository;
    private final PostRatingRepository ratingRepository;
    private final CurrentUserService currentUserService;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final ViewDataService viewDataService;
    private final ProvinceCatalogService provinceCatalogService;

    public PostController(
            TravelPostRepository postRepository,
            CommentRepository commentRepository,
            PostLikeRepository likeRepository,
            PostFavoriteRepository favoriteRepository,
            PostRatingRepository ratingRepository,
            CurrentUserService currentUserService,
            FileStorageService fileStorageService,
            NotificationService notificationService,
            ViewDataService viewDataService,
            ProvinceCatalogService provinceCatalogService) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.likeRepository = likeRepository;
        this.favoriteRepository = favoriteRepository;
        this.ratingRepository = ratingRepository;
        this.currentUserService = currentUserService;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
        this.viewDataService = viewDataService;
        this.provinceCatalogService = provinceCatalogService;
    }

    @GetMapping("/posts/new")
    public String newPostPage(HttpSession session, RedirectAttributes redirectAttributes, Model model) {
        if (!currentUserService.isLoggedIn(session)) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先登录，再发布旅游足迹。");
            return "redirect:/login";
        }
        model.addAttribute("formPost", new TravelPost());
        model.addAttribute("editing", false);
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
            @RequestParam(required = false) MultipartFile photo,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先登录，再发布旅游足迹。");
            return "redirect:/login";
        }

        TravelPost post = new TravelPost();
        if (!fillPost(post, title, location, province, content, travelDate, category, tags, latitude, longitude, photo,
                redirectAttributes)) {
            return "redirect:/posts/new";
        }
        post.setAuthor(currentUser);
        postRepository.save(post);
        redirectAttributes.addFlashAttribute("successMessage", "足迹发布成功，地图和搜索模块都已同步更新。");
        return "redirect:/posts/" + post.getId();
    }

    @GetMapping("/posts/{id}")
    public String postDetail(@PathVariable Long id, Model model, HttpSession session) {
        TravelPost post = postRepository.findById(id).orElseThrow();
        User currentUser = currentUserService.getCurrentUser(session);
        List<Comment> rootComments = commentRepository.findByPostIdAndParentCommentIsNullOrderByCreatedAtAsc(id);

        model.addAttribute("post", post);
        model.addAttribute("comments", rootComments);
        model.addAttribute("replyMap", viewDataService.replyMap(id));
        model.addAttribute("likeCount", likeRepository.countByPostId(id));
        model.addAttribute("favoriteCount", favoriteRepository.countByPostId(id));
        model.addAttribute("commentCount", commentRepository.countByPostId(id));
        model.addAttribute("ratingCount", ratingRepository.countByPostId(id));
        model.addAttribute("ratingAverage", viewDataService.ratingAverages(List.of(post)).getOrDefault(id, 0.0));
        model.addAttribute("liked", currentUser != null && likeRepository.existsByPostIdAndUserId(id, currentUser.getId()));
        model.addAttribute("favorited", currentUser != null && favoriteRepository.existsByPostIdAndUserId(id, currentUser.getId()));
        model.addAttribute("myRating", currentUser == null ? 0
                : ratingRepository.findByPostIdAndUserId(id, currentUser.getId()).map(PostRating::getScore).orElse(0));
        model.addAttribute("ownedByCurrentUser",
                currentUser != null && currentUser.getId().equals(post.getAuthor().getId()));
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
            @RequestParam(required = false) MultipartFile photo,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        TravelPost post = postRepository.findById(id).orElseThrow();
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null || !currentUser.getId().equals(post.getAuthor().getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "只有发布者本人才能编辑这条足迹。");
            return "redirect:/posts/" + id;
        }

        if (!fillPost(post, title, location, province, content, travelDate, category, tags, latitude, longitude, photo,
                redirectAttributes)) {
            return "redirect:/posts/" + id + "/edit";
        }
        postRepository.save(post);
        redirectAttributes.addFlashAttribute("successMessage", "足迹内容已更新。");
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

        favoriteRepository.deleteByPostId(id);
        likeRepository.deleteByPostId(id);
        ratingRepository.deleteByPostId(id);
        commentRepository.deleteByPostId(id);
        postRepository.delete(post);
        redirectAttributes.addFlashAttribute("successMessage", "足迹已删除。");
        return "redirect:/me";
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

        TravelPost post = postRepository.findById(id).orElseThrow();
        Comment comment = new Comment();
        comment.setPost(post);
        comment.setAuthor(currentUser);
        comment.setContent(content.trim());
        if (parentId != null) {
            Optional<Comment> parentComment = commentRepository.findById(parentId);
            parentComment.ifPresent(comment::setParentComment);
        }
        commentRepository.save(comment);

        if (comment.getParentComment() != null) {
            notificationService.notify(
                    comment.getParentComment().getAuthor(),
                    currentUser,
                    NotificationType.REPLY,
                    currentUser.getNickname() + " 回复了你的评论",
                    "/posts/" + id);
        } else {
            notificationService.notify(
                    post.getAuthor(),
                    currentUser,
                    NotificationType.COMMENT,
                    currentUser.getNickname() + " 评论了你的足迹",
                    "/posts/" + id);
        }

        redirectAttributes.addFlashAttribute("successMessage", parentId == null ? "评论发布成功。" : "回复已发送。");
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
            MultipartFile photo,
            RedirectAttributes redirectAttributes) {
        if (title.isBlank() || location.isBlank() || province.isBlank()
                || content.isBlank() || travelDate.isBlank() || category.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "标题、地点、省份、分类、日期和正文都不能为空。");
            return false;
        }

        Optional<String> normalizedProvince = provinceCatalogService.normalizeProvince(province);
        if (normalizedProvince.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "请选择有效的省份后再发布。");
            return false;
        }

        post.setTitle(title.trim());
        post.setLocation(location.trim());
        post.setProvince(normalizedProvince.get());
        post.setContent(content.trim());
        post.setCategory(category.trim());
        post.setTags(tags == null ? "" : tags.trim());

        try {
            post.setTravelDate(LocalDate.parse(travelDate));
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

        try {
            String storedPhoto = fileStorageService.store(photo, "posts");
            if (storedPhoto != null) {
                post.setPhotoPath(storedPhoto);
            }
        } catch (IOException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", "图片上传失败，请稍后再试。");
            return false;
        }
        return true;
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

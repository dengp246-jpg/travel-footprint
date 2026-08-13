package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.model.ContentReviewStatus;
import com.example.travelfootprint.repository.CommentRepository;
import com.example.travelfootprint.repository.PostFavoriteRepository;
import com.example.travelfootprint.repository.PostLikeRepository;
import com.example.travelfootprint.repository.TravelPostRepository;
import com.example.travelfootprint.repository.UserRepository;
import com.example.travelfootprint.service.ContentVisibilityService;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.LocationNormalizationService;
import com.example.travelfootprint.service.ViewDataService;
import jakarta.servlet.http.HttpSession;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

    private static final int PAGE_SIZE = 6;

    private final TravelPostRepository postRepository;
    private final PostLikeRepository likeRepository;
    private final PostFavoriteRepository favoriteRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final ViewDataService viewDataService;
    private final ContentVisibilityService contentVisibilityService;
    private final LocationNormalizationService locationNormalizationService;

    public HomeController(
            TravelPostRepository postRepository,
            PostLikeRepository likeRepository,
            PostFavoriteRepository favoriteRepository,
            CommentRepository commentRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService,
            ViewDataService viewDataService,
            ContentVisibilityService contentVisibilityService,
            LocationNormalizationService locationNormalizationService) {
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.favoriteRepository = favoriteRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.viewDataService = viewDataService;
        this.contentVisibilityService = contentVisibilityService;
        this.locationNormalizationService = locationNormalizationService;
    }

    @GetMapping("/")
    public String home(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam(defaultValue = "all") String contentType,
            @RequestParam(defaultValue = "1") int page,
            Model model,
            HttpSession session) {
        User currentUser = currentUserService.getCurrentUser(session);
        List<TravelPost> posts = contentVisibilityService.approvedPosts(postRepository.findAllByOrderByCreatedAtDesc());
        List<User> users = userRepository.findAll();
        List<com.example.travelfootprint.model.Comment> comments = commentRepository.findAll();
        Set<Long> followingIds = viewDataService.followingUserIds(currentUser);

        List<TravelPost> baseFilteredPosts = posts.stream()
                .filter(post -> !"following".equalsIgnoreCase(scope) || followingIds.contains(post.getAuthor().getId()))
                .filter(post -> q == null || q.isBlank() || containsKeyword(post, q))
                .filter(post -> category == null || category.isBlank() || category.equals(post.getCategory()))
                .filter(post -> matchesLocation(post, location))
                .toList();

        List<TravelPost> filteredPosts = baseFilteredPosts.stream()
                .filter(post -> matchesContentType(post, contentType))
                .toList();

        Map<String, Long> topLocations = filteredPosts.stream()
                .collect(Collectors.groupingBy(
                        locationNormalizationService::normalizeDisplayLocation,
                        Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new));

        List<User> suggestedUsers = users.stream()
                .filter(User::isEnabled)
                .filter(user -> !user.isAdmin())
                .filter(user -> currentUser == null || !user.getId().equals(currentUser.getId()))
                .filter(user -> currentUser == null || !followingIds.contains(user.getId()))
                .limit(5)
                .toList();

        long communityCount = baseFilteredPosts.stream()
                .filter(post -> post.getSourceUrl() == null)
                .count();
        long referenceCount = baseFilteredPosts.stream()
                .filter(post -> post.getSourceUrl() != null)
                .count();

        int totalItems = filteredPosts.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) PAGE_SIZE));
        int currentPage = Math.min(Math.max(page, 1), totalPages);
        int fromIndex = Math.min((currentPage - 1) * PAGE_SIZE, totalItems);
        int toIndex = Math.min(fromIndex + PAGE_SIZE, totalItems);
        List<TravelPost> pagePosts = filteredPosts.subList(fromIndex, toIndex);

        long approvedCommentCount = comments.stream()
                .filter(contentVisibilityService::isApproved)
                .count();
        long pendingPostCount = postsPendingApproval();
        long pendingCommentCount = comments.stream()
                .filter(comment -> comment.getReviewStatus() == ContentReviewStatus.PENDING)
                .count();

        model.addAttribute("posts", pagePosts);
        model.addAttribute("likeCounts", viewDataService.likeCounts(pagePosts));
        model.addAttribute("commentCounts", viewDataService.commentCounts(pagePosts));
        model.addAttribute("favoriteCounts", viewDataService.favoriteCounts(pagePosts));
        model.addAttribute("ratingAverages", viewDataService.ratingAverages(pagePosts));
        model.addAttribute("likedPostIds", viewDataService.likedPostIds(currentUser));
        model.addAttribute("favoritePostIds", viewDataService.favoritePostIds(currentUser));
        model.addAttribute("myRatings", viewDataService.myRatings(pagePosts, currentUser));
        model.addAttribute("followingIds", followingIds);
        model.addAttribute("topLocations", topLocations);
        model.addAttribute("suggestedUsers", suggestedUsers);
        model.addAttribute("query", q == null ? "" : q);
        model.addAttribute("selectedCategory", category == null ? "" : category);
        model.addAttribute("locationQuery", location == null ? "" : location);
        model.addAttribute("scope", scope);
        model.addAttribute("contentType", contentType);
        model.addAttribute("communityCount", communityCount);
        model.addAttribute("referenceCount", referenceCount);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalResults", totalItems);
        model.addAttribute("hasPreviousPage", currentPage > 1);
        model.addAttribute("hasNextPage", currentPage < totalPages);
        model.addAttribute("totalUsers", users.stream().filter(User::isEnabled).count());
        model.addAttribute("totalPosts", posts.size());
        model.addAttribute("totalComments", approvedCommentCount);
        model.addAttribute("totalLikes", likeRepository.count());
        model.addAttribute("totalFavorites", favoriteRepository.count());
        model.addAttribute("pendingPostCount", pendingPostCount);
        model.addAttribute("pendingCommentCount", pendingCommentCount);
        return "index";
    }

    private boolean containsKeyword(TravelPost post, String q) {
        String keyword = q.trim().toLowerCase();
        return post.getTitle().toLowerCase().contains(keyword)
                || locationNormalizationService.normalizeLookupKey(post).contains(keyword.replace(" ", ""))
                || post.getContent().toLowerCase().contains(keyword)
                || post.getAuthor().getNickname().toLowerCase().contains(keyword);
    }

    private boolean matchesLocation(TravelPost post, String location) {
        if (location == null || location.isBlank()) {
            return true;
        }
        return locationNormalizationService.normalizeLookupKey(post)
                .contains(locationNormalizationService.normalizeLookupKey(post.getProvince(), location));
    }

    private boolean matchesContentType(TravelPost post, String contentType) {
        if ("community".equalsIgnoreCase(contentType)) {
            return post.getSourceUrl() == null;
        }
        if ("reference".equalsIgnoreCase(contentType)) {
            return post.getSourceUrl() != null;
        }
        return true;
    }

    private long postsPendingApproval() {
        return postRepository.findAll().stream()
                .filter(post -> post.getReviewStatus() == ContentReviewStatus.PENDING)
                .count();
    }
}

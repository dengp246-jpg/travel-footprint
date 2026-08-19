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
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
        List<User> users = userRepository.findAll();
        Set<Long> followingIds = viewDataService.followingUserIds(currentUser);

        Specification<TravelPost> baseFilter = feedSpecification(
                q, category, location, scope, "all", followingIds);
        Specification<TravelPost> selectedFilter = feedSpecification(
                q, category, location, scope, contentType, followingIds);
        int requestedPage = Math.max(page, 1) - 1;
        Page<TravelPost> postPage = postRepository.findAll(
                selectedFilter,
                PageRequest.of(requestedPage, PAGE_SIZE,
                        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        if (requestedPage >= postPage.getTotalPages() && postPage.getTotalPages() > 0) {
            requestedPage = postPage.getTotalPages() - 1;
            postPage = postRepository.findAll(
                    selectedFilter,
                    PageRequest.of(requestedPage, PAGE_SIZE,
                            Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        }
        List<TravelPost> pagePosts = postPage.getContent();

        Map<String, Long> topLocations = pagePosts.stream()
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

        long communityCount = postRepository.count(baseFilter.and(sourceTypeSpecification("community")));
        long referenceCount = postRepository.count(baseFilter.and(sourceTypeSpecification("reference")));
        long totalItems = postPage.getTotalElements();
        int totalPages = Math.max(1, postPage.getTotalPages());
        int currentPage = postPage.isEmpty() ? 1 : postPage.getNumber() + 1;

        long approvedCommentCount = commentRepository
                .countByReviewStatusAndAuthorEnabledTrue(ContentReviewStatus.APPROVED);
        long pendingPostCount = postRepository.count((root, query, builder) ->
                builder.equal(root.get("reviewStatus"), ContentReviewStatus.PENDING));
        long pendingCommentCount = commentRepository
                .countByReviewStatusAndAuthorEnabledTrue(ContentReviewStatus.PENDING);

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
        model.addAttribute("totalUsers", userRepository.countByEnabledTrue());
        model.addAttribute("totalPosts", postRepository.count(feedSpecification(null, null, null, "all", "all", Set.of())));
        model.addAttribute("totalComments", approvedCommentCount);
        model.addAttribute("totalLikes", likeRepository.count());
        model.addAttribute("totalFavorites", favoriteRepository.count());
        model.addAttribute("pendingPostCount", pendingPostCount);
        model.addAttribute("pendingCommentCount", pendingCommentCount);
        return "index";
    }

    private Specification<TravelPost> feedSpecification(
            String q,
            String category,
            String location,
            String scope,
            String contentType,
            Set<Long> followingIds) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.or(
                    builder.equal(root.get("reviewStatus"), ContentReviewStatus.APPROVED),
                    builder.isNull(root.get("reviewStatus"))));
            predicates.add(builder.or(
                    builder.equal(root.get("visibility"), com.example.travelfootprint.model.PostVisibility.PUBLIC),
                    builder.isNull(root.get("visibility"))));
            predicates.add(builder.isTrue(root.get("author").get("enabled")));
            if ("following".equalsIgnoreCase(scope)) {
                predicates.add(followingIds.isEmpty()
                        ? builder.disjunction()
                        : root.get("author").get("id").in(followingIds));
            }
            if (q != null && !q.isBlank()) {
                String keyword = "%" + q.trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), keyword),
                        builder.like(builder.lower(root.get("content")), keyword),
                        builder.like(builder.lower(root.get("location")), keyword),
                        builder.like(builder.lower(root.get("province")), keyword),
                        builder.like(builder.lower(root.get("author").get("nickname")), keyword)));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(builder.equal(root.get("category"), category));
            }
            if (location != null && !location.isBlank()) {
                String locationKeyword = "%" + location.trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("location")), locationKeyword),
                        builder.like(builder.lower(root.get("province")), locationKeyword)));
            }
            Predicate sourcePredicate = sourceTypeSpecification(contentType).toPredicate(root, query, builder);
            if (sourcePredicate != null) {
                predicates.add(sourcePredicate);
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<TravelPost> sourceTypeSpecification(String contentType) {
        return (root, query, builder) -> {
            if ("community".equalsIgnoreCase(contentType)) {
                return builder.isNull(root.get("sourceUrl"));
            }
            if ("reference".equalsIgnoreCase(contentType)) {
                return builder.isNotNull(root.get("sourceUrl"));
            }
            return builder.conjunction();
        };
    }
}

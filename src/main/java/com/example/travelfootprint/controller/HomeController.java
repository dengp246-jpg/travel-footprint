package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.CommentRepository;
import com.example.travelfootprint.repository.PostFavoriteRepository;
import com.example.travelfootprint.repository.PostLikeRepository;
import com.example.travelfootprint.repository.TravelPostRepository;
import com.example.travelfootprint.repository.UserRepository;
import com.example.travelfootprint.service.CurrentUserService;
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

    private final TravelPostRepository postRepository;
    private final PostLikeRepository likeRepository;
    private final PostFavoriteRepository favoriteRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final ViewDataService viewDataService;

    public HomeController(
            TravelPostRepository postRepository,
            PostLikeRepository likeRepository,
            PostFavoriteRepository favoriteRepository,
            CommentRepository commentRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService,
            ViewDataService viewDataService) {
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.favoriteRepository = favoriteRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.viewDataService = viewDataService;
    }

    @GetMapping("/")
    public String home(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "all") String scope,
            Model model,
            HttpSession session) {
        User currentUser = currentUserService.getCurrentUser(session);
        List<TravelPost> posts = postRepository.findAllByOrderByCreatedAtDesc();
        Set<Long> followingIds = viewDataService.followingUserIds(currentUser);

        List<TravelPost> filteredPosts = posts.stream()
                .filter(post -> !"following".equalsIgnoreCase(scope) || followingIds.contains(post.getAuthor().getId()))
                .filter(post -> q == null || q.isBlank() || containsKeyword(post, q))
                .filter(post -> category == null || category.isBlank() || category.equals(post.getCategory()))
                .filter(post -> location == null || location.isBlank()
                        || post.getLocation().toLowerCase().contains(location.trim().toLowerCase()))
                .toList();

        Map<String, Long> topLocations = filteredPosts.stream()
                .collect(Collectors.groupingBy(TravelPost::getLocation, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new));

        List<User> suggestedUsers = userRepository.findAll().stream()
                .filter(user -> currentUser == null || !user.getId().equals(currentUser.getId()))
                .filter(user -> currentUser == null || !followingIds.contains(user.getId()))
                .limit(5)
                .toList();

        model.addAttribute("posts", filteredPosts);
        model.addAttribute("likeCounts", viewDataService.likeCounts(filteredPosts));
        model.addAttribute("commentCounts", viewDataService.commentCounts(filteredPosts));
        model.addAttribute("favoriteCounts", viewDataService.favoriteCounts(filteredPosts));
        model.addAttribute("ratingAverages", viewDataService.ratingAverages(filteredPosts));
        model.addAttribute("likedPostIds", viewDataService.likedPostIds(currentUser));
        model.addAttribute("favoritePostIds", viewDataService.favoritePostIds(currentUser));
        model.addAttribute("myRatings", viewDataService.myRatings(filteredPosts, currentUser));
        model.addAttribute("followingIds", followingIds);
        model.addAttribute("topLocations", topLocations);
        model.addAttribute("suggestedUsers", suggestedUsers);
        model.addAttribute("query", q == null ? "" : q);
        model.addAttribute("selectedCategory", category == null ? "" : category);
        model.addAttribute("locationQuery", location == null ? "" : location);
        model.addAttribute("scope", scope);
        model.addAttribute("totalUsers", userRepository.count());
        model.addAttribute("totalPosts", postRepository.count());
        model.addAttribute("totalComments", commentRepository.count());
        model.addAttribute("totalLikes", likeRepository.count());
        model.addAttribute("totalFavorites", favoriteRepository.count());
        return "index";
    }

    private boolean containsKeyword(TravelPost post, String q) {
        String keyword = q.trim().toLowerCase();
        return post.getTitle().toLowerCase().contains(keyword)
                || post.getLocation().toLowerCase().contains(keyword)
                || post.getContent().toLowerCase().contains(keyword)
                || post.getAuthor().getNickname().toLowerCase().contains(keyword);
    }
}

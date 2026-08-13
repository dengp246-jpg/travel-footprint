package com.example.travelfootprint.service;

import com.example.travelfootprint.model.ContentReviewStatus;
import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.TravelPostRepository;
import com.example.travelfootprint.repository.UserFollowRepository;
import com.example.travelfootprint.repository.UserRepository;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class TravelCompanionService {

    private final UserRepository userRepository;
    private final TravelPostRepository postRepository;
    private final UserFollowRepository followRepository;
    private final ProvinceCatalogService provinceCatalogService;
    private final ContentVisibilityService contentVisibilityService;

    public TravelCompanionService(
            UserRepository userRepository,
            TravelPostRepository postRepository,
            UserFollowRepository followRepository,
            ProvinceCatalogService provinceCatalogService,
            ContentVisibilityService contentVisibilityService) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.followRepository = followRepository;
        this.provinceCatalogService = provinceCatalogService;
        this.contentVisibilityService = contentVisibilityService;
    }

    public DiscoveryView build(User currentUser) {
        List<TravelPost> myPosts = postRepository.findByAuthorIdOrderByCreatedAtDesc(currentUser.getId());
        Set<String> myProvinces = provinces(myPosts);
        Set<String> myCategories = categories(myPosts);
        Set<Long> followingIds = followRepository.findByFollowerId(currentUser.getId()).stream()
                .map(follow -> follow.getFollowing().getId())
                .collect(java.util.stream.Collectors.toSet());

        List<CompanionMatch> matches = userRepository.findAll().stream()
                .filter(User::isEnabled)
                .filter(user -> !user.isAdmin() && !user.getId().equals(currentUser.getId()))
                .map(user -> match(user, myProvinces, myCategories, followingIds.contains(user.getId())))
                .sorted(Comparator.comparingInt(CompanionMatch::score).reversed()
                        .thenComparing(match -> match.user().getJoinedAt(), Comparator.reverseOrder()))
                .limit(12)
                .toList();

        List<TravelPost> followingPosts = postRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(post -> post.getReviewStatus() == ContentReviewStatus.APPROVED)
                .filter(post -> followingIds.contains(post.getAuthor().getId()))
                .filter(post -> contentVisibilityService.canViewPost(currentUser, post))
                .limit(8)
                .toList();
        return new DiscoveryView(matches, followingPosts, followingIds.size(), myProvinces.size(), myCategories.size());
    }

    private CompanionMatch match(User user, Set<String> myProvinces, Set<String> myCategories, boolean following) {
        List<TravelPost> posts = postRepository.findByAuthorIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(post -> post.getReviewStatus() == ContentReviewStatus.APPROVED)
                .filter(post -> contentVisibilityService.isPublicPost(post))
                .toList();
        Set<String> sharedProvinces = intersection(myProvinces, provinces(posts));
        Set<String> sharedCategories = intersection(myCategories, categories(posts));
        int score = sharedProvinces.size() * 3 + sharedCategories.size() * 2 + Math.min(posts.size(), 5);
        int matchPercent = Math.min(98, 42 + sharedProvinces.size() * 18
                + sharedCategories.size() * 12 + Math.min(posts.size(), 4) * 3);
        String reason;
        if (!sharedProvinces.isEmpty()) {
            reason = "你们都去过 " + String.join("、", sharedProvinces.stream().limit(2).toList());
        } else if (!sharedCategories.isEmpty()) {
            reason = "都喜欢 " + String.join("、", sharedCategories.stream().limit(2).toList());
        } else if (!posts.isEmpty()) {
            reason = "记录了 " + posts.size() + " 条公开旅行足迹";
        } else {
            reason = "刚加入旅迹，等待发现共同目的地";
        }
        return new CompanionMatch(user, matchPercent, score, reason, posts.size(), following);
    }

    private Set<String> provinces(List<TravelPost> posts) {
        Set<String> values = new HashSet<>();
        posts.forEach(post -> provinceCatalogService.resolveProvince(post.getProvince(), post.getLocation())
                .ifPresent(values::add));
        return values;
    }

    private Set<String> categories(List<TravelPost> posts) {
        Set<String> values = new HashSet<>();
        posts.stream().map(TravelPost::getCategory)
                .filter(value -> value != null && !value.isBlank()).forEach(values::add);
        return values;
    }

    private Set<String> intersection(Set<String> left, Set<String> right) {
        Set<String> result = new HashSet<>(left);
        result.retainAll(right);
        return result;
    }

    public record DiscoveryView(
            List<CompanionMatch> matches,
            List<TravelPost> followingPosts,
            long followingCount,
            long provinceInterestCount,
            long categoryInterestCount) {
    }

    public record CompanionMatch(
            User user,
            int matchPercent,
            int score,
            String reason,
            int postCount,
            boolean following) {
    }
}

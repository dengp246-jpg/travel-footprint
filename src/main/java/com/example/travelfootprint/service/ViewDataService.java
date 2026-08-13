package com.example.travelfootprint.service;

import com.example.travelfootprint.model.Comment;
import com.example.travelfootprint.model.ContentReviewStatus;
import com.example.travelfootprint.model.PostFavorite;
import com.example.travelfootprint.model.PostLike;
import com.example.travelfootprint.model.PostRating;
import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.model.UserFollow;
import com.example.travelfootprint.repository.CommentRepository;
import com.example.travelfootprint.repository.PostFavoriteRepository;
import com.example.travelfootprint.repository.PostLikeRepository;
import com.example.travelfootprint.repository.PostRatingRepository;
import com.example.travelfootprint.repository.UserFollowRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ViewDataService {

    private final PostLikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final PostFavoriteRepository favoriteRepository;
    private final PostRatingRepository ratingRepository;
    private final UserFollowRepository followRepository;
    private final ContentVisibilityService contentVisibilityService;

    public ViewDataService(
            PostLikeRepository likeRepository,
            CommentRepository commentRepository,
            PostFavoriteRepository favoriteRepository,
            PostRatingRepository ratingRepository,
            UserFollowRepository followRepository,
            ContentVisibilityService contentVisibilityService) {
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.favoriteRepository = favoriteRepository;
        this.ratingRepository = ratingRepository;
        this.followRepository = followRepository;
        this.contentVisibilityService = contentVisibilityService;
    }

    public Map<Long, Long> likeCounts(List<TravelPost> posts) {
        List<Long> postIds = postIds(posts);
        return withLongDefaults(postIds, postIds.isEmpty() ? List.of() : likeRepository.countGroupedByPostIds(postIds));
    }

    public Map<Long, Long> commentCounts(List<TravelPost> posts) {
        List<Long> postIds = postIds(posts);
        return withLongDefaults(
                postIds,
                postIds.isEmpty()
                        ? List.of()
                        : commentRepository.countVisibleGroupedByPostIds(postIds, ContentReviewStatus.APPROVED));
    }

    public Map<Long, Long> favoriteCounts(List<TravelPost> posts) {
        List<Long> postIds = postIds(posts);
        return withLongDefaults(postIds, postIds.isEmpty() ? List.of() : favoriteRepository.countGroupedByPostIds(postIds));
    }

    public Map<Long, Double> ratingAverages(List<TravelPost> posts) {
        List<Long> postIds = postIds(posts);
        Map<Long, Double> averages = postIds.stream()
                .collect(Collectors.toMap(id -> id, id -> 0.0, (left, right) -> left, LinkedHashMap::new));
        if (!postIds.isEmpty()) {
            ratingRepository.averageGroupedByPostIds(postIds).forEach(row ->
                    averages.put(((Number) row[0]).longValue(), ((Number) row[1]).doubleValue()));
        }
        return averages;
    }

    public Map<Long, Integer> myRatings(List<TravelPost> posts, User currentUser) {
        if (currentUser == null) {
            return Map.of();
        }
        List<Long> postIds = postIds(posts);
        if (postIds.isEmpty()) {
            return Map.of();
        }
        return ratingRepository.findByPostIdInAndUserId(postIds, currentUser.getId()).stream()
                .collect(Collectors.toMap(
                        rating -> rating.getPost().getId(),
                        PostRating::getScore,
                        (left, right) -> right,
                        LinkedHashMap::new));
    }

    public Set<Long> likedPostIds(User currentUser) {
        if (currentUser == null) {
            return Set.of();
        }
        return likeRepository.findByUserId(currentUser.getId()).stream()
                .map(PostLike::getPost)
                .map(TravelPost::getId)
                .collect(Collectors.toSet());
    }

    public Set<Long> favoritePostIds(User currentUser) {
        if (currentUser == null) {
            return Set.of();
        }
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId()).stream()
                .map(PostFavorite::getPost)
                .map(TravelPost::getId)
                .collect(Collectors.toSet());
    }

    public Set<Long> followingUserIds(User currentUser) {
        if (currentUser == null) {
            return Set.of();
        }
        return followRepository.findByFollowerId(currentUser.getId()).stream()
                .map(UserFollow::getFollowing)
                .map(User::getId)
                .collect(Collectors.toSet());
    }

    public Map<Long, List<Comment>> replyMap(Long postId) {
        List<Comment> allComments = approvedComments(postId);
        return allComments.stream()
                .filter(comment -> comment.getParentComment() != null)
                .collect(Collectors.groupingBy(comment -> comment.getParentComment().getId(), LinkedHashMap::new, Collectors.toList()));
    }

    public List<Comment> approvedRootComments(Long postId) {
        return approvedComments(postId).stream()
                .filter(comment -> comment.getParentComment() == null)
                .toList();
    }

    public long approvedCommentCount(Long postId) {
        return approvedComments(postId).size();
    }

    private List<Comment> approvedComments(Long postId) {
        return contentVisibilityService.approvedComments(commentRepository.findByPostIdOrderByCreatedAtAsc(postId));
    }

    private List<Long> postIds(List<TravelPost> posts) {
        return posts.stream().map(TravelPost::getId).toList();
    }

    private Map<Long, Long> withLongDefaults(List<Long> postIds, List<Object[]> rows) {
        Map<Long, Long> counts = postIds.stream()
                .collect(Collectors.toMap(id -> id, id -> 0L, (left, right) -> left, LinkedHashMap::new));
        rows.forEach(row -> counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue()));
        return counts;
    }
}

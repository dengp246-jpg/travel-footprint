package com.example.travelfootprint.service;

import com.example.travelfootprint.model.Comment;
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

    public ViewDataService(
            PostLikeRepository likeRepository,
            CommentRepository commentRepository,
            PostFavoriteRepository favoriteRepository,
            PostRatingRepository ratingRepository,
            UserFollowRepository followRepository) {
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.favoriteRepository = favoriteRepository;
        this.ratingRepository = ratingRepository;
        this.followRepository = followRepository;
    }

    public Map<Long, Long> likeCounts(List<TravelPost> posts) {
        return posts.stream().collect(Collectors.toMap(TravelPost::getId, post -> likeRepository.countByPostId(post.getId())));
    }

    public Map<Long, Long> commentCounts(List<TravelPost> posts) {
        return posts.stream().collect(Collectors.toMap(TravelPost::getId, post -> commentRepository.countByPostId(post.getId())));
    }

    public Map<Long, Long> favoriteCounts(List<TravelPost> posts) {
        return posts.stream().collect(Collectors.toMap(TravelPost::getId, post -> favoriteRepository.countByPostId(post.getId())));
    }

    public Map<Long, Double> ratingAverages(List<TravelPost> posts) {
        return posts.stream().collect(Collectors.toMap(TravelPost::getId, post -> {
            List<PostRating> ratings = ratingRepository.findByPostId(post.getId());
            if (ratings.isEmpty()) {
                return 0.0;
            }
            return ratings.stream().mapToInt(PostRating::getScore).average().orElse(0.0);
        }));
    }

    public Map<Long, Integer> myRatings(List<TravelPost> posts, User currentUser) {
        if (currentUser == null) {
            return Map.of();
        }
        Map<Long, Integer> ratings = new LinkedHashMap<>();
        for (TravelPost post : posts) {
            ratingRepository.findByPostIdAndUserId(post.getId(), currentUser.getId())
                    .ifPresent(rating -> ratings.put(post.getId(), rating.getScore()));
        }
        return ratings;
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
        List<Comment> allComments = commentRepository.findByPostIdOrderByCreatedAtAsc(postId);
        return allComments.stream()
                .filter(comment -> comment.getParentComment() != null)
                .collect(Collectors.groupingBy(comment -> comment.getParentComment().getId(), LinkedHashMap::new, Collectors.toList()));
    }
}

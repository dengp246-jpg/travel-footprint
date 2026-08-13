package com.example.travelfootprint.service;

import com.example.travelfootprint.model.Comment;
import com.example.travelfootprint.model.ContentReviewStatus;
import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.model.PostVisibility;
import com.example.travelfootprint.repository.UserFollowRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ContentVisibilityService {

    private final UserFollowRepository followRepository;

    public ContentVisibilityService(UserFollowRepository followRepository) {
        this.followRepository = followRepository;
    }

    public boolean isAdmin(User user) {
        return user != null && user.isAdmin() && user.isEnabled();
    }

    public boolean isApproved(TravelPost post) {
        return post != null && post.getReviewStatus() == ContentReviewStatus.APPROVED;
    }

    public boolean isPublicPost(TravelPost post) {
        return isApproved(post)
                && post.getVisibility() == PostVisibility.PUBLIC
                && post.getAuthor() != null
                && post.getAuthor().isEnabled();
    }

    public boolean isApproved(Comment comment) {
        return comment != null && comment.getReviewStatus() == ContentReviewStatus.APPROVED;
    }

    public boolean canViewPost(User viewer, TravelPost post) {
        if (post == null || post.getAuthor() == null || !post.getAuthor().isEnabled()) {
            return false;
        }
        if (isApproved(post)) {
            if (post.getVisibility() == PostVisibility.PUBLIC) {
                return true;
            }
            if (viewer == null) {
                return false;
            }
            if (isAdmin(viewer) || viewer.getId().equals(post.getAuthor().getId())) {
                return true;
            }
            return post.getVisibility() == PostVisibility.FOLLOWERS
                    && followRepository.existsByFollowerIdAndFollowingId(viewer.getId(), post.getAuthor().getId());
        }
        return viewer != null
                && (isAdmin(viewer) || viewer.getId().equals(post.getAuthor().getId()));
    }

    public boolean canManagePost(User viewer, TravelPost post) {
        return viewer != null
                && post != null
                && (isAdmin(viewer) || viewer.getId().equals(post.getAuthor().getId()));
    }

    public boolean canViewComment(User viewer, Comment comment) {
        if (comment == null || comment.getAuthor() == null || !comment.getAuthor().isEnabled()) {
            return false;
        }
        if (isApproved(comment)) {
            return true;
        }
        return viewer != null
                && (isAdmin(viewer) || viewer.getId().equals(comment.getAuthor().getId()));
    }

    public ContentReviewStatus defaultPostStatus(User author, boolean trustedSource) {
        return trustedSource || isAdmin(author) ? ContentReviewStatus.APPROVED : ContentReviewStatus.PENDING;
    }

    public ContentReviewStatus defaultCommentStatus(User author) {
        return isAdmin(author) ? ContentReviewStatus.APPROVED : ContentReviewStatus.PENDING;
    }

    public List<TravelPost> approvedPosts(List<TravelPost> posts) {
        return posts.stream()
                .filter(this::isApproved)
                .filter(post -> post.getVisibility() == PostVisibility.PUBLIC)
                .filter(post -> post.getAuthor() != null && post.getAuthor().isEnabled())
                .toList();
    }

    public List<TravelPost> visiblePostsForProfile(List<TravelPost> posts, User viewer, User owner) {
        boolean includeOwnQueue = viewer != null
                && owner != null
                && (viewer.getId().equals(owner.getId()) || isAdmin(viewer));
        return posts.stream()
                .filter(post -> includeOwnQueue ? canViewPost(viewer, post) : canViewPost(viewer, post))
                .filter(post -> post.getAuthor() != null && post.getAuthor().isEnabled())
                .toList();
    }

    public List<Comment> approvedComments(List<Comment> comments) {
        return comments.stream()
                .filter(this::isApproved)
                .filter(comment -> comment.getAuthor() != null && comment.getAuthor().isEnabled())
                .toList();
    }
}

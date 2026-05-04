package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.UserFollow;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {

    Optional<UserFollow> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    List<UserFollow> findByFollowerId(Long followerId);

    List<UserFollow> findByFollowingId(Long followingId);

    long countByFollowerId(Long followerId);

    long countByFollowingId(Long followingId);
}

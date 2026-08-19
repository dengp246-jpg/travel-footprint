package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    List<User> findByIdNotOrderByNicknameAsc(Long id);

    boolean existsByUsername(String username);

    boolean existsByAvatarPath(String avatarPath);

    long countByEnabledTrue();
}

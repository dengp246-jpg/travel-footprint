package com.example.travelfootprint.repository;

import com.example.travelfootprint.model.MiniAppSession;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MiniAppSessionRepository extends JpaRepository<MiniAppSession, Long> {

    Optional<MiniAppSession> findByTokenHash(String tokenHash);

    long deleteByTokenHash(String tokenHash);

    long deleteByExpiresAtBefore(LocalDateTime cutoff);
}

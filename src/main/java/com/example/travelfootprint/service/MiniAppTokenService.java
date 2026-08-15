package com.example.travelfootprint.service;

import com.example.travelfootprint.model.MiniAppSession;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.MiniAppSessionRepository;
import com.example.travelfootprint.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MiniAppTokenService {

    private final MiniAppSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final int validityDays;

    public MiniAppTokenService(
            MiniAppSessionRepository sessionRepository,
            UserRepository userRepository,
            @Value("${app.mini.session-validity-days:30}") int validityDays) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.validityDays = Math.max(1, validityDays);
    }

    @Transactional
    public String issueToken(User user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        sessionRepository.deleteByExpiresAtBefore(now);
        MiniAppSession session = new MiniAppSession();
        session.setTokenHash(hash(token));
        session.setUser(user);
        session.setCreatedAt(now);
        session.setExpiresAt(now.plusDays(validityDays));
        sessionRepository.save(session);
        return token;
    }

    @Transactional
    public Optional<User> findUser(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Optional<MiniAppSession> storedSession = sessionRepository.findByTokenHash(hash(token.trim()));
        if (storedSession.isEmpty()) {
            return Optional.empty();
        }
        MiniAppSession session = storedSession.get();
        if (!session.getExpiresAt().isAfter(LocalDateTime.now())) {
            sessionRepository.delete(session);
            return Optional.empty();
        }
        Optional<User> user = userRepository.findById(session.getUser().getId()).filter(User::isEnabled);
        if (user.isEmpty()) {
            sessionRepository.delete(session);
        }
        return user;
    }

    @Transactional
    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        sessionRepository.deleteByTokenHash(hash(token.trim()));
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

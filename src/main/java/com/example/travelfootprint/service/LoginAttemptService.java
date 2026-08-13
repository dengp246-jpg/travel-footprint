package com.example.travelfootprint.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

    private final ConcurrentHashMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();
    private final int maximumAttempts;
    private final Duration window;

    public LoginAttemptService(
            @Value("${app.security.login.max-attempts:8}") int maximumAttempts,
            @Value("${app.security.login.window-minutes:15}") long windowMinutes) {
        this.maximumAttempts = Math.max(3, maximumAttempts);
        this.window = Duration.ofMinutes(Math.max(1, windowMinutes));
    }

    public boolean isBlocked(String username, String remoteAddress) {
        String key = key(username, remoteAddress);
        AttemptWindow current = attempts.get(key);
        if (current == null) return false;
        if (current.startedAt().plus(window).isBefore(Instant.now())) {
            attempts.remove(key, current);
            return false;
        }
        return current.failures() >= maximumAttempts;
    }

    public void recordFailure(String username, String remoteAddress) {
        Instant now = Instant.now();
        attempts.compute(key(username, remoteAddress), (key, current) -> {
            if (current == null || current.startedAt().plus(window).isBefore(now)) {
                return new AttemptWindow(1, now);
            }
            return new AttemptWindow(current.failures() + 1, current.startedAt());
        });
        if (attempts.size() > 5000) {
            attempts.entrySet().removeIf(entry -> entry.getValue().startedAt().plus(window).isBefore(now));
        }
    }

    public void recordSuccess(String username, String remoteAddress) {
        attempts.remove(key(username, remoteAddress));
    }

    private String key(String username, String remoteAddress) {
        String normalizedUser = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        String normalizedAddress = remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress.trim();
        return normalizedUser + "|" + normalizedAddress;
    }

    private record AttemptWindow(int failures, Instant startedAt) { }
}

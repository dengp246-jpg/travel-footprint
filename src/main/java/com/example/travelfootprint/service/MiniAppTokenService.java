package com.example.travelfootprint.service;

import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.UserRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class MiniAppTokenService {

    private final Map<String, Long> tokenToUserId = new ConcurrentHashMap<>();
    private final UserRepository userRepository;

    public MiniAppTokenService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String issueToken(User user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenToUserId.put(token, user.getId());
        return token;
    }

    public Optional<User> findUser(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Long userId = tokenToUserId.get(token.trim());
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findById(userId);
    }

    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        tokenToUserId.remove(token.trim());
    }
}

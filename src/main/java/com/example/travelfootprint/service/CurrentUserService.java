package com.example.travelfootprint.service;

import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    public static final String SESSION_USER_ID = "currentUserId";

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getCurrentUser(HttpSession session) {
        Object userId = session.getAttribute(SESSION_USER_ID);
        if (userId instanceof Long id) {
            return resolveActiveUser(id, session);
        }
        if (userId instanceof Integer id) {
            return resolveActiveUser(id.longValue(), session);
        }
        return null;
    }

    public boolean isLoggedIn(HttpSession session) {
        return getCurrentUser(session) != null;
    }

    public void login(HttpSession session, User user) {
        session.setAttribute(SESSION_USER_ID, user.getId());
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }

    private User resolveActiveUser(Long userId, HttpSession session) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.isEnabled()) {
            return user;
        }
        session.removeAttribute(SESSION_USER_ID);
        return null;
    }
}

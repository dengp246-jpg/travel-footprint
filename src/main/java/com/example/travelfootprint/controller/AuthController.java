package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.UserRepository;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.LoginAttemptService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final LoginAttemptService loginAttemptService;

    public AuthController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            CurrentUserService currentUserService,
            LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
        this.loginAttemptService = loginAttemptService;
    }

    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        if (currentUserService.isLoggedIn(session)) {
            return "redirect:/";
        }
        return "login";
    }

    @PostMapping("/login")
    public String login(
            @RequestParam String username,
            @RequestParam String password,
            HttpServletRequest request,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        String normalizedUsername = username == null ? "" : username.trim();
        String remoteAddress = request.getRemoteAddr();
        if (loginAttemptService.isBlocked(normalizedUsername, remoteAddress)) {
            redirectAttributes.addFlashAttribute("errorMessage", "登录尝试过于频繁，请稍后再试。");
            return "redirect:/login";
        }
        return userRepository.findByUsername(normalizedUsername)
                .filter(User::isEnabled)
                .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()))
                .map(user -> {
                    loginAttemptService.recordSuccess(normalizedUsername, remoteAddress);
                    request.changeSessionId();
                    user.setLastLoginAt(java.time.LocalDateTime.now());
                    userRepository.save(user);
                    currentUserService.login(session, user);
                    redirectAttributes.addFlashAttribute("successMessage", "登录成功，欢迎回来。");
                    return "redirect:/";
                })
                .orElseGet(() -> {
                    loginAttemptService.recordFailure(normalizedUsername, remoteAddress);
                    redirectAttributes.addFlashAttribute("errorMessage", "用户名或密码不正确。");
                    return "redirect:/login";
                });
    }

    @GetMapping("/register")
    public String registerPage(HttpSession session) {
        if (currentUserService.isLoggedIn(session)) {
            return "redirect:/";
        }
        return "register";
    }

    @PostMapping("/register")
    public String register(
            @RequestParam String username,
            @RequestParam String nickname,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            @RequestParam(required = false) String bio,
            HttpServletRequest request,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        String normalizedUsername = username.trim();
        String normalizedNickname = nickname.trim();

        if (normalizedUsername.isEmpty() || normalizedNickname.isEmpty() || password.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "用户名、昵称和密码都不能为空。");
            return "redirect:/register";
        }
        if (normalizedUsername.length() > 50 || normalizedNickname.length() > 50
                || (bio != null && bio.trim().length() > 500)) {
            redirectAttributes.addFlashAttribute("errorMessage", "用户名和昵称最多 50 字，个人简介最多 500 字。");
            return "redirect:/register";
        }
        if (normalizedUsername.chars().anyMatch(Character::isWhitespace)) {
            redirectAttributes.addFlashAttribute("errorMessage", "用户名不能包含空格。");
            return "redirect:/register";
        }
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            redirectAttributes.addFlashAttribute("errorMessage", "密码长度应为 6 到 72 个字符。");
            return "redirect:/register";
        }
        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "两次输入的密码不一致。");
            return "redirect:/register";
        }
        if (userRepository.findByUsername(normalizedUsername).isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", "该用户名已被注册，请更换一个。");
            return "redirect:/register";
        }

        User user = new User();
        user.setUsername(normalizedUsername);
        user.setNickname(normalizedNickname);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setBio(bio == null ? "" : bio.trim());
        user.setEnabled(true);
        user.setAdmin(false);
        user.setLastLoginAt(java.time.LocalDateTime.now());
        user.setPasswordChangedAt(java.time.LocalDateTime.now());
        userRepository.save(user);
        request.changeSessionId();
        currentUserService.login(session, user);

        redirectAttributes.addFlashAttribute("successMessage", "注册成功，开始记录你的旅行故事吧。");
        return "redirect:/";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes redirectAttributes) {
        currentUserService.logout(session);
        redirectAttributes.addFlashAttribute("successMessage", "你已安全退出登录。");
        return "redirect:/";
    }
}

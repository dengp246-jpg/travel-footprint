package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.UserRepository;
import com.example.travelfootprint.service.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;

    public AuthController(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            CurrentUserService currentUserService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
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
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        return userRepository.findByUsername(username.trim())
                .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()))
                .map(user -> {
                    currentUserService.login(session, user);
                    redirectAttributes.addFlashAttribute("successMessage", "登录成功，欢迎回来。");
                    return "redirect:/";
                })
                .orElseGet(() -> {
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
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        String normalizedUsername = username.trim();
        String normalizedNickname = nickname.trim();

        if (normalizedUsername.isEmpty() || normalizedNickname.isEmpty() || password.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "用户名、昵称和密码都不能为空。");
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
        userRepository.save(user);
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

package com.example.travelfootprint.controller;

import com.example.travelfootprint.model.NotificationType;
import com.example.travelfootprint.model.PostFavorite;
import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.model.UserFollow;
import com.example.travelfootprint.repository.PostFavoriteRepository;
import com.example.travelfootprint.repository.TravelPostRepository;
import com.example.travelfootprint.repository.UserFollowRepository;
import com.example.travelfootprint.repository.UserRepository;
import com.example.travelfootprint.service.ContentVisibilityService;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.FileStorageService;
import com.example.travelfootprint.service.LocationNormalizationService;
import com.example.travelfootprint.service.NotificationService;
import com.example.travelfootprint.service.ProvinceCatalogService;
import com.example.travelfootprint.service.ViewDataService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProfileController {

    private final UserRepository userRepository;
    private final TravelPostRepository postRepository;
    private final UserFollowRepository followRepository;
    private final PostFavoriteRepository favoriteRepository;
    private final CurrentUserService currentUserService;
    private final ViewDataService viewDataService;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final ProvinceCatalogService provinceCatalogService;
    private final ContentVisibilityService contentVisibilityService;
    private final LocationNormalizationService locationNormalizationService;
    private final PasswordEncoder passwordEncoder;

    public ProfileController(
            UserRepository userRepository,
            TravelPostRepository postRepository,
            UserFollowRepository followRepository,
            PostFavoriteRepository favoriteRepository,
            CurrentUserService currentUserService,
            ViewDataService viewDataService,
            FileStorageService fileStorageService,
            NotificationService notificationService,
            ProvinceCatalogService provinceCatalogService,
            ContentVisibilityService contentVisibilityService,
            LocationNormalizationService locationNormalizationService,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.followRepository = followRepository;
        this.favoriteRepository = favoriteRepository;
        this.currentUserService = currentUserService;
        this.viewDataService = viewDataService;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
        this.provinceCatalogService = provinceCatalogService;
        this.contentVisibilityService = contentVisibilityService;
        this.locationNormalizationService = locationNormalizationService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/me")
    public String myProfile(HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先登录，再查看个人主页。");
            return "redirect:/login";
        }
        return "redirect:/users/" + currentUser.getId();
    }

    @GetMapping("/users/{id}")
    public String userProfile(
            @PathVariable Long id,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String scenicKeyword,
            @RequestParam(required = false) LocalDate publishedFrom,
            @RequestParam(required = false) LocalDate publishedTo,
            Model model,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User profileUser = userRepository.findById(id).orElseThrow();
        User currentUser = currentUserService.getCurrentUser(session);
        boolean canViewQueue = currentUser != null
                && (currentUser.getId().equals(id) || contentVisibilityService.isAdmin(currentUser));
        if (!profileUser.isEnabled() && !canViewQueue) {
            redirectAttributes.addFlashAttribute("errorMessage", "该用户主页暂不可见。");
            return "redirect:/";
        }

        List<TravelPost> posts = contentVisibilityService.visiblePostsForProfile(
                        postRepository.findByAuthorIdOrderByCreatedAtDesc(id),
                        currentUser,
                        profileUser).stream()
                .filter(post -> matchesProvince(post, province))
                .filter(post -> matchesScenicKeyword(post, scenicKeyword))
                .filter(post -> matchesPublishedDate(post, publishedFrom, publishedTo))
                .toList();
        Map<Long, Long> likeCounts = viewDataService.likeCounts(posts);
        Map<Long, Long> commentCounts = viewDataService.commentCounts(posts);
        Map<Long, Long> favoriteCounts = viewDataService.favoriteCounts(posts);

        model.addAttribute("profileUser", profileUser);
        model.addAttribute("posts", posts);
        model.addAttribute("likeCounts", likeCounts);
        model.addAttribute("commentCounts", commentCounts);
        model.addAttribute("favoriteCounts", favoriteCounts);
        model.addAttribute("ratingAverages", viewDataService.ratingAverages(posts));
        model.addAttribute("likedPostIds", viewDataService.likedPostIds(currentUser));
        model.addAttribute("favoritePostIds", viewDataService.favoritePostIds(currentUser));
        model.addAttribute("profileLikes", likeCounts.values().stream().mapToLong(Long::longValue).sum());
        model.addAttribute("profileComments", commentCounts.values().stream().mapToLong(Long::longValue).sum());
        model.addAttribute("profileFavorites", favoriteCounts.values().stream().mapToLong(Long::longValue).sum());
        model.addAttribute("followerCount", followRepository.countByFollowingId(id));
        model.addAttribute("followingCount", followRepository.countByFollowerId(id));
        model.addAttribute("isSelf", currentUser != null && currentUser.getId().equals(id));
        model.addAttribute("showModerationStatus", canViewQueue);
        model.addAttribute("isFollowing",
                currentUser != null && followRepository.existsByFollowerIdAndFollowingId(currentUser.getId(), id));
        model.addAttribute("provinceOptions", provinceCatalogService.provinceNames());
        model.addAttribute("selectedProvinceFilter", province == null ? "" : province.trim());
        model.addAttribute("scenicKeyword", scenicKeyword == null ? "" : scenicKeyword.trim());
        model.addAttribute("publishedFrom", publishedFrom);
        model.addAttribute("publishedTo", publishedTo);
        return "profile";
    }

    private boolean matchesProvince(TravelPost post, String province) {
        if (province == null || province.isBlank()) {
            return true;
        }
        String normalizedProvince = province.trim();
        return provinceCatalogService.resolveProvince(post.getProvince(), post.getLocation())
                .map(normalizedProvince::equals)
                .orElse(false);
    }

    private boolean matchesScenicKeyword(TravelPost post, String scenicKeyword) {
        if (scenicKeyword == null || scenicKeyword.isBlank()) {
            return true;
        }
        String keyword = scenicKeyword.trim().replace(" ", "").toLowerCase();
        String title = post.getTitle() == null ? "" : post.getTitle().replace(" ", "").toLowerCase();
        String location = locationNormalizationService.normalizeLookupKey(post);
        return title.contains(keyword) || location.contains(keyword);
    }

    private boolean matchesPublishedDate(TravelPost post, LocalDate publishedFrom, LocalDate publishedTo) {
        LocalDate createdDate = post.getCreatedAt() == null ? null : post.getCreatedAt().toLocalDate();
        if (createdDate == null) {
            return publishedFrom == null && publishedTo == null;
        }
        boolean afterStart = publishedFrom == null || !createdDate.isBefore(publishedFrom);
        boolean beforeEnd = publishedTo == null || !createdDate.isAfter(publishedTo);
        return afterStart && beforeEnd;
    }

    @PostMapping("/users/{id}/follow")
    public String toggleFollow(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先登录，再关注其他旅行者。");
            return "redirect:/login";
        }
        if (currentUser.getId().equals(id)) {
            redirectAttributes.addFlashAttribute("errorMessage", "你不能关注自己。");
            return "redirect:/users/" + id;
        }

        User targetUser = userRepository.findById(id).orElseThrow();
        if (!targetUser.isEnabled()) {
            redirectAttributes.addFlashAttribute("errorMessage", "该用户当前不可关注。");
            return "redirect:/";
        }
        followRepository.findByFollowerIdAndFollowingId(currentUser.getId(), id).ifPresentOrElse(
                followRepository::delete,
                () -> {
                    UserFollow follow = new UserFollow();
                    follow.setFollower(currentUser);
                    follow.setFollowing(targetUser);
                    followRepository.save(follow);
                    notificationService.notify(
                            targetUser,
                            currentUser,
                            NotificationType.FOLLOW,
                            currentUser.getNickname() + " 关注了你",
                            "/users/" + currentUser.getId());
                });
        return "redirect:/users/" + id;
    }

    @GetMapping("/favorites")
    public String favorites(Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先登录，再查看收藏清单。");
            return "redirect:/login";
        }

        List<TravelPost> posts = favoriteRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId()).stream()
                .map(PostFavorite::getPost)
                .filter(post -> contentVisibilityService.canViewPost(currentUser, post))
                .toList();
        model.addAttribute("posts", posts);
        model.addAttribute("likeCounts", viewDataService.likeCounts(posts));
        model.addAttribute("commentCounts", viewDataService.commentCounts(posts));
        model.addAttribute("favoriteCounts", viewDataService.favoriteCounts(posts));
        model.addAttribute("ratingAverages", viewDataService.ratingAverages(posts));
        model.addAttribute("likedPostIds", viewDataService.likedPostIds(currentUser));
        model.addAttribute("favoritePostIds", viewDataService.favoritePostIds(currentUser));
        return "favorites";
    }

    @GetMapping("/settings")
    public String settings(HttpSession session, RedirectAttributes redirectAttributes) {
        if (!currentUserService.isLoggedIn(session)) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先登录，再编辑个人资料。");
            return "redirect:/login";
        }
        return "settings";
    }

    @PostMapping("/settings")
    public String updateSettings(
            @RequestParam String nickname,
            @RequestParam(required = false) String bio,
            @RequestParam(required = false) MultipartFile avatar,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "请先登录，再编辑个人资料。");
            return "redirect:/login";
        }
        if (nickname.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "昵称不能为空。");
            return "redirect:/settings";
        }

        String normalizedNickname = nickname.trim();
        String normalizedBio = bio == null ? "" : bio.trim();
        if (normalizedNickname.length() > 50 || normalizedBio.length() > 500) {
            redirectAttributes.addFlashAttribute("errorMessage", "昵称最多 50 字，个人简介最多 500 字。");
            return "redirect:/settings";
        }

        currentUser.setNickname(normalizedNickname);
        currentUser.setBio(normalizedBio);
        try {
            String avatarPath = fileStorageService.store(avatar, "avatars");
            if (avatarPath != null) {
                currentUser.setAvatarPath(avatarPath);
            }
        } catch (IOException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/settings";
        }

        userRepository.save(currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "个人资料已更新。");
        return "redirect:/users/" + currentUser.getId();
    }

    @PostMapping("/settings/password")
    public String changePassword(
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            HttpServletRequest request,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User currentUser = currentUserService.getCurrentUser(session);
        if (currentUser == null) {
            return "redirect:/login";
        }
        if (!passwordEncoder.matches(currentPassword, currentUser.getPasswordHash())) {
            redirectAttributes.addFlashAttribute("errorMessage", "当前密码不正确。");
            return "redirect:/settings#account-security";
        }
        if (newPassword.length() < 8 || newPassword.length() > 72) {
            redirectAttributes.addFlashAttribute("errorMessage", "新密码长度应为 8 到 72 个字符。");
            return "redirect:/settings#account-security";
        }
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "两次输入的新密码不一致。");
            return "redirect:/settings#account-security";
        }
        if (passwordEncoder.matches(newPassword, currentUser.getPasswordHash())) {
            redirectAttributes.addFlashAttribute("errorMessage", "新密码不能与当前密码相同。");
            return "redirect:/settings#account-security";
        }
        currentUser.setPasswordHash(passwordEncoder.encode(newPassword));
        currentUser.setPasswordChangedAt(java.time.LocalDateTime.now());
        userRepository.save(currentUser);
        request.changeSessionId();
        currentUserService.login(session, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "密码已更新，当前设备仍保持登录。其他旧会话将在失效后退出。");
        return "redirect:/settings#account-security";
    }
}

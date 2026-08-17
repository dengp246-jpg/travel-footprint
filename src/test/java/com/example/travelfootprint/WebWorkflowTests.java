package com.example.travelfootprint;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.example.travelfootprint.model.ContentReviewStatus;
import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.TripPlan;
import com.example.travelfootprint.model.TripPlanStatus;
import com.example.travelfootprint.model.PostVisibility;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.model.TripPlanMember;
import com.example.travelfootprint.model.TripPlanMemberStatus;
import com.example.travelfootprint.model.Notification;
import com.example.travelfootprint.model.NotificationType;
import com.example.travelfootprint.model.DestinationWishPriority;
import com.example.travelfootprint.model.DestinationWishStatus;
import com.example.travelfootprint.model.TripChecklistCategory;
import com.example.travelfootprint.repository.TravelPostRepository;
import com.example.travelfootprint.repository.TravelPostPhotoRepository;
import com.example.travelfootprint.repository.TravelExpenseRepository;
import com.example.travelfootprint.repository.TripPlanRepository;
import com.example.travelfootprint.repository.TravelGoalRepository;
import com.example.travelfootprint.repository.UserRepository;
import com.example.travelfootprint.repository.TripPlanMemberRepository;
import com.example.travelfootprint.repository.NotificationRepository;
import com.example.travelfootprint.repository.DestinationWishRepository;
import com.example.travelfootprint.repository.TripPlanActivityRepository;
import com.example.travelfootprint.repository.TripChecklistItemRepository;
import com.example.travelfootprint.repository.RecommendationDismissalRepository;
import com.example.travelfootprint.service.CurrentUserService;
import com.example.travelfootprint.service.DestinationMapService;
import com.example.travelfootprint.service.FileStorageService;
import com.example.travelfootprint.service.MiniAppTokenService;
import com.example.travelfootprint.service.SmartItineraryService;
import com.example.travelfootprint.service.TripReadinessService;
import java.time.LocalDate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WebWorkflowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TravelPostRepository postRepository;

    @Autowired
    private TravelPostPhotoRepository photoRepository;

    @Autowired
    private TripPlanRepository tripPlanRepository;

    @Autowired
    private TravelExpenseRepository expenseRepository;

    @Autowired
    private TravelGoalRepository goalRepository;

    @Autowired
    private TripPlanMemberRepository tripPlanMemberRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DestinationWishRepository destinationWishRepository;

    @Autowired
    private TripPlanActivityRepository tripPlanActivityRepository;

    @Autowired
    private TripChecklistItemRepository tripChecklistItemRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MiniAppTokenService miniAppTokenService;

    @Autowired
    private DestinationMapService destinationMapService;

    @Autowired
    private SmartItineraryService smartItineraryService;

    @Autowired
    private TripReadinessService tripReadinessService;

    @Autowired
    private RecommendationDismissalRepository recommendationDismissalRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @Test
    void ordinaryUserEditReturnsApprovedPostToPendingReview() throws Exception {
        User user = createUser(true);
        TravelPost post = createPost(user, ContentReviewStatus.APPROVED);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, user.getId());

        mockMvc.perform(post("/posts/{id}/edit", post.getId())
                        .with(csrf())
                        .session(session)
                        .param("title", "审核后修改的标题")
                        .param("location", "杭州 西湖")
                        .param("province", "浙江")
                        .param("content", "这是一段修改后的旅行记录，需要重新经过审核。")
                        .param("travelDate", "2026-08-01")
                        .param("category", "自然风光")
                        .param("tags", "西湖,散步"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/posts/" + post.getId()));

        TravelPost updated = postRepository.findById(post.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(ContentReviewStatus.PENDING, updated.getReviewStatus());
    }

    @Test
    void miniApiHidesPendingPostFromPublicFeedButShowsItToOwner() throws Exception {
        User user = createUser(true);
        TravelPost pendingPost = createPost(user, ContentReviewStatus.PENDING);
        String uniqueMarker = pendingPost.getTitle().substring(pendingPost.getTitle().indexOf('-') + 1);
        String token = miniAppTokenService.issueToken(user);

        mockMvc.perform(get("/api/mini/posts"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(uniqueMarker))));

        mockMvc.perform(get("/api/mini/posts")
                        .header("X-Mini-Token", token)
                        .param("mine", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(uniqueMarker)));
    }

    @Test
    void disabledUserCannotContinueUsingMiniAppToken() throws Exception {
        User user = createUser(true);
        String token = miniAppTokenService.issueToken(user);
        user.setEnabled(false);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/mini/auth/me").header("X-Mini-Token", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void browserPostWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/logout"))
                .andExpect(status().isForbidden());
    }

    @Test
    void mapFiltersPostsByTravelYearAndCategory() throws Exception {
        User user = createUser(true);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        TravelPost expected = createPost(
                user,
                ContentReviewStatus.APPROVED,
                "MapYearA-" + suffix,
                "北京 故宫",
                "北京",
                "城市漫游",
                LocalDate.of(2024, 5, 2));
        TravelPost excluded = createPost(
                user,
                ContentReviewStatus.APPROVED,
                "MapYearB-" + suffix,
                "上海 外滩",
                "上海",
                "城市漫游",
                LocalDate.of(2025, 5, 2));

        mockMvc.perform(get("/map")
                        .param("year", "2024")
                        .param("category", "城市漫游"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(expected.getTitle())))
                .andExpect(content().string(not(containsString(excluded.getTitle()))))
                .andExpect(content().string(containsString("data-focus-map")))
                .andExpect(content().string(not(containsString("data-map-routes"))))
                .andExpect(content().string(containsString("map-heat-layer")))
                .andExpect(content().string(containsString("data-map-clusters")))
                .andExpect(content().string(containsString("data-map-story-drawer")))
                .andExpect(content().string(containsString("data-map-fullscreen")))
                .andExpect(content().string(containsString("全屏地图")))
                .andExpect(content().string(containsString("map-intelligence-panel")));
    }

    @Test
    void travelRoutesOnlyAppearOnPersonalMapAndUseActualTravelDate() throws Exception {
        User user = createUser(true);
        TravelPost personalPost = createPost(user, ContentReviewStatus.APPROVED);
        personalPost.setVideoPath("/uploads/test-map-story-video.mp4");
        postRepository.saveAndFlush(personalPost);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, user.getId());

        mockMvc.perform(get("/map").param("mode", "personal").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(personalPost.getTitle())))
                .andExpect(content().string(containsString("data-map-routes")))
                .andExpect(content().string(containsString("data-map-timeline")))
                .andExpect(content().string(containsString("时空旅行故事")))
                .andExpect(content().string(containsString("data-travel-date")))
                .andExpect(content().string(containsString("data-video-path")))
                .andExpect(content().string(containsString("按照实际旅行日期")));
    }

    @Test
    void travelPassportBuildsAchievementsFromRealFootprintsAndRequiresLogin() throws Exception {
        User user = createUser(true);
        TravelPost first = createPost(user, ContentReviewStatus.APPROVED, "杭州春日", "浙江 杭州 西湖", "浙江",
                "自然风光", LocalDate.of(2025, 3, 12));
        TravelPost second = createPost(user, ContentReviewStatus.APPROVED, "杭州再见", "浙江 杭州 西湖", "浙江",
                "城市漫游", LocalDate.of(2026, 8, 1));
        second.setVideoPath("/uploads/test-passport-video.mp4");
        postRepository.saveAndFlush(second);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, user.getId());

        mockMvc.perform(get("/passport").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("旅行护照")))
                .andExpect(content().string(containsString(user.getNickname())))
                .andExpect(content().string(containsString("动态旅行家")))
                .andExpect(content().string(containsString("故地重游")))
                .andExpect(content().string(containsString(first.getTitle())))
                .andExpect(content().string(containsString("播放我的旅行故事")));

        mockMvc.perform(get("/passport"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void mapPlacementRejectsLandmarkAnchorsFromConflictingProvince() {
        TravelPost conflictingPost = new TravelPost();
        conflictingPost.setProvince("江西");
        conflictingPost.setLocation("湖南长沙");

        TravelPost changshaPost = new TravelPost();
        changshaPost.setProvince("湖南");
        changshaPost.setLocation("湖南长沙");

        DestinationMapService.MapPoint correctedPoint = destinationMapService.resolvePoint(conflictingPost).orElseThrow();
        DestinationMapService.MapPoint changshaPoint = destinationMapService.resolvePoint(changshaPost).orElseThrow();

        org.junit.jupiter.api.Assertions.assertTrue(correctedPoint.left() > changshaPoint.left() + 2.0);
    }

    @Test
    void mapPlacementPrefersValidStoredCoordinates() {
        TravelPost coordinatePost = new TravelPost();
        coordinatePost.setProvince("浙江");
        coordinatePost.setLocation("浙江 未收录景点");
        coordinatePost.setLatitude(30.2431);
        coordinatePost.setLongitude(120.1500);

        TravelPost westLakePost = new TravelPost();
        westLakePost.setProvince("浙江");
        westLakePost.setLocation("浙江 杭州 西湖");

        DestinationMapService.MapPoint coordinatePoint = destinationMapService.resolvePoint(coordinatePost).orElseThrow();
        DestinationMapService.MapPoint westLakePoint = destinationMapService.resolvePoint(westLakePost).orElseThrow();

        org.junit.jupiter.api.Assertions.assertEquals(westLakePoint.left(), coordinatePoint.left(), 0.1);
        org.junit.jupiter.api.Assertions.assertEquals(westLakePoint.top(), coordinatePoint.top(), 0.1);
    }

    @Test
    void postEditorProvidesDeterministicOfflineLocationSuggestions() throws Exception {
        User user = createUser(true);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, user.getId());

        mockMvc.perform(get("/posts/new").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-location-assistant")))
                .andExpect(content().string(containsString("data-location-suggestion")))
                .andExpect(content().string(containsString("杭州 西湖")))
                .andExpect(content().string(containsString("data-latitude-input")))
                .andExpect(content().string(containsString("data-longitude-input")))
                .andExpect(content().string(containsString("发布前隐私预览")))
                .andExpect(content().string(containsString("data-visibility-select")));

        DestinationMapService.LocationSuggestion westLake = destinationMapService.locationSuggestions().stream()
                .filter(suggestion -> suggestion.location().contains("西湖"))
                .findFirst()
                .orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("浙江", westLake.province());
        org.junit.jupiter.api.Assertions.assertEquals(120.1551, westLake.longitude(), 0.0001);
        org.junit.jupiter.api.Assertions.assertEquals(30.2741, westLake.latitude(), 0.0001);
    }

    @Test
    void foregroundArrivalReminderMatchesLocationAndPrefillsNewFootprint() throws Exception {
        User user = createUser(true);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, user.getId());

        mockMvc.perform(get("/api/location/arrival-match")
                        .session(session)
                        .param("longitude", "120.1551")
                        .param("latitude", "30.2741"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value("杭州 西湖"))
                .andExpect(jsonPath("$.province").value("浙江"))
                .andExpect(jsonPath("$.matched").value(true));

        mockMvc.perform(get("/posts/new")
                        .session(session)
                        .param("arrivalLocation", "杭州 西湖")
                        .param("arrivalProvince", "浙江")
                        .param("arrivalLatitude", "30.2741")
                        .param("arrivalLongitude", "120.1551")
                        .param("arrivalDate", "2026-08-17"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("已从到访提醒带入当前位置")))
                .andExpect(content().string(containsString("杭州 西湖")))
                .andExpect(content().string(containsString("2026-08-17")))
                .andExpect(content().string(containsString("checked")));

        mockMvc.perform(get("/api/location/arrival-match")
                        .param("longitude", "120.1551")
                        .param("latitude", "30.2741"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mapSupportsKeywordPhotoFilterAndTravelDateSorting() throws Exception {
        User user = createUser(true);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        TravelPost oldestPhoto = createPost(
                user,
                ContentReviewStatus.APPROVED,
                "PhotoOld-" + suffix,
                "浙江 杭州 西湖",
                "浙江",
                "城市漫游",
                LocalDate.of(2023, 4, 1));
        oldestPhoto.setPhotoPath("/uploads/posts/map-old.jpg");
        postRepository.saveAndFlush(oldestPhoto);

        TravelPost latestPhoto = createPost(
                user,
                ContentReviewStatus.APPROVED,
                "PhotoNew-" + suffix,
                "北京 故宫",
                "北京",
                "城市漫游",
                LocalDate.of(2025, 4, 1));
        latestPhoto.setPhotoPath("/uploads/posts/map-new.jpg");
        postRepository.saveAndFlush(latestPhoto);

        TravelPost noPhoto = createPost(
                user,
                ContentReviewStatus.APPROVED,
                "PhotoNone-" + suffix,
                "上海 外滩",
                "上海",
                "城市漫游",
                LocalDate.of(2024, 4, 1));

        String responseBody = mockMvc.perform(get("/map")
                        .param("q", suffix)
                        .param("hasPhoto", "true")
                        .param("sort", "oldest"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(oldestPhoto.getTitle())))
                .andExpect(content().string(containsString(latestPhoto.getTitle())))
                .andExpect(content().string(not(containsString(noPhoto.getTitle()))))
                .andExpect(content().string(containsString("data-share-map")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.junit.jupiter.api.Assertions.assertTrue(
                responseBody.indexOf(oldestPhoto.getTitle()) < responseBody.indexOf(latestPhoto.getTitle()));
    }

    @Test
    void travelReportsSupportWeeklyMonthlyAndYearlyPeriods() throws Exception {
        User user = createUser(true);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        TravelPost monday = createPost(
                user, ContentReviewStatus.APPROVED, "周一足迹-" + suffix,
                "浙江 杭州 西湖", "浙江", "城市漫游", LocalDate.of(2026, 8, 3));
        TravelPost saturday = createPost(
                user, ContentReviewStatus.PENDING, "周六足迹-" + suffix,
                "北京 故宫", "北京", "人文古迹", LocalDate.of(2026, 8, 8));
        TravelPost july = createPost(
                user, ContentReviewStatus.APPROVED, "七月足迹-" + suffix,
                "湖南 张家界", "湖南", "自然风光", LocalDate.of(2026, 7, 31));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, user.getId());

        mockMvc.perform(get("/reports").session(session)
                        .param("period", "week").param("date", "2026-08-05"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("WEEKLY JOURNEY")))
                .andExpect(content().string(containsString(monday.getTitle())))
                .andExpect(content().string(containsString(saturday.getTitle())))
                .andExpect(content().string(not(containsString(july.getTitle()))));

        mockMvc.perform(get("/reports").session(session)
                        .param("period", "month").param("date", "2026-08-05"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("MONTHLY JOURNEY")))
                .andExpect(content().string(containsString("2026年8月")))
                .andExpect(content().string(not(containsString(july.getTitle()))));

        mockMvc.perform(get("/reports").session(session)
                        .param("period", "year").param("date", "2026-08-05"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("YEARLY JOURNEY")))
                .andExpect(content().string(containsString(july.getTitle())))
                .andExpect(content().string(containsString("分享报告")))
                .andExpect(content().string(containsString("打印 / 保存")));
    }

    @Test
    void travelReportRequiresLogin() throws Exception {
        mockMvc.perform(get("/reports"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void footprintSupportsOrderedMultiPhotoGalleryAndTripPlanLink() throws Exception {
        User user = createUser(true);
        TripPlan plan = createPlan(user, "相册测试行程");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, user.getId());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        byte[] firstPng = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1};
        byte[] secondPng = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 2};

        mockMvc.perform(multipart("/posts")
                        .file(new MockMultipartFile("photos", "first.png", "image/png", firstPng))
                        .file(new MockMultipartFile("photos", "second.png", "image/png", secondPng))
                        .with(csrf()).session(session)
                        .param("title", "多图足迹-" + suffix)
                        .param("location", "杭州 西湖")
                        .param("province", "浙江")
                        .param("content", "用于验证多图相册、排序、封面和行程关联。")
                        .param("travelDate", "2026-08-08")
                        .param("category", "城市漫游")
                        .param("tags", "相册")
                        .param("tripPlanId", plan.getId().toString())
                        .param("coverPhotoIndex", "1"))
                .andExpect(status().is3xxRedirection());

        TravelPost saved = postRepository.findByAuthorIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(post -> post.getTitle().equals("多图足迹-" + suffix)).findFirst().orElseThrow();
        var photos = photoRepository.findByPostIdOrderBySortOrderAscIdAsc(saved.getId());
        org.junit.jupiter.api.Assertions.assertEquals(2, photos.size());
        org.junit.jupiter.api.Assertions.assertFalse(photos.get(0).isCover());
        org.junit.jupiter.api.Assertions.assertTrue(photos.get(1).isCover());
        org.junit.jupiter.api.Assertions.assertEquals(photos.get(1).getPhotoPath(), saved.getPhotoPath());
        org.junit.jupiter.api.Assertions.assertEquals(plan.getId(), saved.getTripPlan().getId());
    }

    @Test
    void footprintVideoUploadUsesPostVisibilityAndRendersPlayer() throws Exception {
        User user = createUser(true);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, user.getId());
        String title = "视频足迹-" + UUID.randomUUID().toString().substring(0, 8);
        MockMultipartFile video = new MockMultipartFile(
                "video",
                "journey.mp4",
                "video/mp4",
                new byte[] {0x00, 0x00, 0x00, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'});

        mockMvc.perform(multipart("/posts")
                        .file(video)
                        .with(csrf())
                        .session(session)
                        .param("title", title)
                        .param("location", "浙江 杭州 西湖")
                        .param("province", "浙江")
                        .param("content", "带有旅行视频的足迹内容。")
                        .param("travelDate", "2026-08-17")
                        .param("category", "自然风光")
                        .param("tags", "视频,旅行"))
                .andExpect(status().is3xxRedirection());

        TravelPost post = postRepository.findAll().stream()
                .filter(item -> title.equals(item.getTitle()))
                .findFirst()
                .orElseThrow();
        String videoPath = post.getVideoPath();
        org.junit.jupiter.api.Assertions.assertNotNull(videoPath);
        try {
            mockMvc.perform(get(videoPath)).andExpect(status().isNotFound());
            mockMvc.perform(get(videoPath).session(session))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "video/mp4"))
                    .andExpect(header().string("Cache-Control", containsString("no-store")));
            mockMvc.perform(get("/posts/{id}", post.getId()).session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<video")))
                    .andExpect(content().string(containsString(videoPath)));
        } finally {
            fileStorageService.delete(videoPath);
        }
    }

    @Test
    void travelCalendarGroupsFootprintsByTravelDate() throws Exception {
        User user = createUser(true);
        TravelPost august = createPost(user, ContentReviewStatus.PENDING, "八月日历足迹", "北京 故宫", "北京", "人文古迹", LocalDate.of(2026, 8, 8));
        TravelPost july = createPost(user, ContentReviewStatus.APPROVED, "七月日历足迹", "浙江 杭州 西湖", "浙江", "城市漫游", LocalDate.of(2026, 7, 20));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, user.getId());

        mockMvc.perform(get("/calendar").session(session).param("month", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2026年8月")))
                .andExpect(content().string(containsString(august.getTitle())))
                .andExpect(content().string(not(containsString(july.getTitle()))));
    }

    @Test
    void travelLedgerCreatesCategorizedExpenseAndShowsMonthlyTotal() throws Exception {
        User user = createUser(true);
        TripPlan plan = createPlan(user, "账本测试行程");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, user.getId());

        mockMvc.perform(post("/expenses").with(csrf()).session(session)
                        .param("amount", "128.50")
                        .param("category", "TRANSPORT")
                        .param("occurredOn", "2026-08-08")
                        .param("note", "高铁票")
                        .param("tripPlanId", plan.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/expenses?month=2026-08"));

        org.junit.jupiter.api.Assertions.assertEquals(1, expenseRepository.findByTripPlanId(plan.getId()).size());
        mockMvc.perform(get("/expenses").session(session).param("month", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("128.50")))
                .andExpect(content().string(containsString("高铁票")))
                .andExpect(content().string(containsString("交通")));
    }

    @Test
    void privateFootprintIsVisibleOnlyToOwnerAndPersonalMap() throws Exception {
        User owner = createUser(true);
        TravelPost privatePost = createPost(owner, ContentReviewStatus.APPROVED, "仅自己可见足迹", "浙江 杭州 西湖", "浙江", "城市漫游", LocalDate.of(2026, 8, 8));
        privatePost.setVisibility(PostVisibility.PRIVATE);
        postRepository.saveAndFlush(privatePost);
        MockHttpSession ownerSession = new MockHttpSession();
        ownerSession.setAttribute(CurrentUserService.SESSION_USER_ID, owner.getId());

        mockMvc.perform(get("/posts/{id}", privatePost.getId()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/posts/{id}", privatePost.getId()).session(ownerSession))
                .andExpect(status().isOk()).andExpect(content().string(containsString(privatePost.getTitle())));
        mockMvc.perform(get("/map").session(ownerSession).param("mode", "personal"))
                .andExpect(status().isOk()).andExpect(content().string(containsString(privatePost.getTitle())));
        mockMvc.perform(get("/map"))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString(privatePost.getTitle()))));
    }

    @Test
    void annualRecapAndReportComparisonUsePersonalTravelData() throws Exception {
        User user = createUser(true);
        TravelPost post = createPost(user, ContentReviewStatus.APPROVED, "年度回忆足迹", "湖南 张家界", "湖南", "自然风光", LocalDate.of(2026, 5, 2));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, user.getId());

        mockMvc.perform(get("/reports").session(session).param("period", "year").param("date", "2026-06-01"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("与上一期相比")))
                .andExpect(content().string(containsString(post.getTitle())));
        mockMvc.perform(get("/recap").session(session).param("year", "2026"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("年度旅行回忆")))
                .andExpect(content().string(containsString("张家界")));
    }

    @Test
    void mapSupportsProvinceAndCityDrilldown() throws Exception {
        User user = createUser(true);
        TravelPost hangzhou = createPost(user, ContentReviewStatus.APPROVED, "杭州下钻足迹", "浙江 杭州 西湖", "浙江", "城市漫游", LocalDate.of(2026, 5, 2));
        TravelPost ningbo = createPost(user, ContentReviewStatus.APPROVED, "宁波下钻足迹", "浙江 宁波 天一阁", "浙江", "人文古迹", LocalDate.of(2026, 5, 3));

        mockMvc.perform(get("/map").param("province", "浙江").param("city", "杭州"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(hangzhou.getTitle())))
                .andExpect(content().string(not(containsString(ningbo.getTitle()))))
                .andExpect(content().string(containsString("地点下钻")));
    }

    @Test
    void travelGoalTracksProgressAndPersonalExportOmitsPassword() throws Exception {
        User user = createUser(true);
        TravelPost post = createPost(user, ContentReviewStatus.APPROVED, "数据导出足迹", "北京 故宫", "北京", "人文古迹", LocalDate.of(2026, 8, 8));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, user.getId());

        mockMvc.perform(post("/goals").with(csrf()).session(session)
                        .param("title", "发布一条足迹").param("type", "FOOTPRINTS")
                        .param("targetValue", "1").param("targetYear", "2026"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/goals"));
        mockMvc.perform(get("/goals").session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString("100%")))
                .andExpect(content().string(containsString("目标已完成")));
        org.junit.jupiter.api.Assertions.assertEquals(1, goalRepository.findByOwnerIdOrderByTargetYearDescCreatedAtDesc(user.getId()).size());

        mockMvc.perform(get("/settings/export").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().string(containsString(post.getTitle())))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void tripPlanInvitationCanBeAcceptedAndSharedPlanCanReceiveFootprint() throws Exception {
        User owner = createUser(true);
        User companion = createUser(true);
        TripPlan plan = createPlan(owner, "同行协作计划");
        MockHttpSession ownerSession = new MockHttpSession();
        ownerSession.setAttribute(CurrentUserService.SESSION_USER_ID, owner.getId());
        MockHttpSession companionSession = new MockHttpSession();
        companionSession.setAttribute(CurrentUserService.SESSION_USER_ID, companion.getId());

        mockMvc.perform(post("/plans/{id}/invite", plan.getId()).with(csrf()).session(ownerSession)
                        .param("username", companion.getUsername()))
                .andExpect(status().is3xxRedirection());
        TripPlanMember invitation = tripPlanMemberRepository
                .findByTripPlanIdAndUserId(plan.getId(), companion.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(TripPlanMemberStatus.PENDING, invitation.getStatus());

        mockMvc.perform(post("/plans/invitations/{id}/respond", invitation.getId()).with(csrf()).session(companionSession)
                        .param("accept", "true"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/plans"));
        org.junit.jupiter.api.Assertions.assertEquals(TripPlanMemberStatus.ACCEPTED,
                tripPlanMemberRepository.findById(invitation.getId()).orElseThrow().getStatus());

        String title = "同行足迹-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(multipart("/posts").with(csrf()).session(companionSession)
                        .param("title", title).param("location", "浙江 杭州 西湖").param("province", "浙江")
                        .param("content", "同行者为共同旅行计划补充的一条旅行足迹记录。")
                        .param("travelDate", "2026-08-08").param("category", "城市漫游")
                        .param("tags", "同行").param("tripPlanId", plan.getId().toString()))
                .andExpect(status().is3xxRedirection());
        TravelPost linked = postRepository.findByAuthorIdOrderByCreatedAtDesc(companion.getId()).stream()
                .filter(post -> title.equals(post.getTitle())).findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(plan.getId(), linked.getTripPlan().getId());
    }

    @Test
    void planOwnerPermissionsPreventOutsiderInvitations() throws Exception {
        User owner = createUser(true);
        User outsider = createUser(true);
        User target = createUser(true);
        TripPlan plan = createPlan(owner, "权限测试计划");
        MockHttpSession outsiderSession = new MockHttpSession();
        outsiderSession.setAttribute(CurrentUserService.SESSION_USER_ID, outsider.getId());

        mockMvc.perform(post("/plans/{id}/invite", plan.getId()).with(csrf()).session(outsiderSession)
                        .param("username", target.getUsername()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/plans"));
        org.junit.jupiter.api.Assertions.assertTrue(
                tripPlanMemberRepository.findByTripPlanIdAndUserId(plan.getId(), target.getId()).isEmpty());
    }

    @Test
    void travelCompanionDiscoveryUsesSharedPublicInterests() throws Exception {
        User viewer = createUser(true);
        User candidate = createUser(true);
        createPost(viewer, ContentReviewStatus.APPROVED, "我的杭州足迹", "浙江 杭州 西湖", "浙江", "城市漫游", LocalDate.of(2026, 3, 1));
        createPost(candidate, ContentReviewStatus.APPROVED, "同好杭州足迹", "浙江 杭州 运河", "浙江", "城市漫游", LocalDate.of(2026, 3, 2));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, viewer.getId());

        mockMvc.perform(get("/discover").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(candidate.getNickname())))
                .andExpect(content().string(containsString("你们都去过")))
                .andExpect(content().string(containsString("不读取私信或私密足迹")));
    }

    @Test
    void passwordCanBeChangedAfterCurrentPasswordVerification() throws Exception {
        User user = createUser(true);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, user.getId());

        mockMvc.perform(post("/settings/password").with(csrf()).session(session)
                        .param("currentPassword", "123456")
                        .param("newPassword", "new-secure-87654321")
                        .param("confirmPassword", "new-secure-87654321"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings#account-security"));
        User updated = userRepository.findById(user.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches("new-secure-87654321", updated.getPasswordHash()));
        org.junit.jupiter.api.Assertions.assertNotNull(updated.getPasswordChangedAt());
    }

    @Test
    void notificationRemainsUnreadUntilOpened() throws Exception {
        User user = createUser(true);
        Notification notification = new Notification();
        notification.setRecipient(user);
        notification.setType(NotificationType.PLAN_REMINDER);
        notification.setMessage("行程即将开始");
        notification.setLinkPath("/plans");
        notification = notificationRepository.saveAndFlush(notification);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, user.getId());

        mockMvc.perform(get("/notifications").session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString("未读")));
        org.junit.jupiter.api.Assertions.assertNull(notificationRepository.findById(notification.getId()).orElseThrow().getReadAt());
        mockMvc.perform(get("/notifications/{id}/open", notification.getId()).session(session))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/plans"));
        org.junit.jupiter.api.Assertions.assertNotNull(notificationRepository.findById(notification.getId()).orElseThrow().getReadAt());
    }

    @Test
    void destinationWishlistConvertsIntoCollaborativePlan() throws Exception {
        User user = createUser(true);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, user.getId());

        mockMvc.perform(post("/wishlist").with(csrf()).session(session)
                        .param("destination", "喀纳斯")
                        .param("province", "新疆")
                        .param("note", "想在秋天看层林与湖泊")
                        .param("priority", DestinationWishPriority.HIGH.name())
                        .param("targetYear", "2027"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/wishlist"));
        var wish = destinationWishRepository.findByOwnerIdOrderByCreatedAtDesc(user.getId()).get(0);
        org.junit.jupiter.api.Assertions.assertEquals(DestinationWishStatus.WISH, wish.getStatus());

        mockMvc.perform(post("/wishlist/{id}/convert", wish.getId()).with(csrf()).session(session)
                        .param("startDate", "2027-09-18").param("endDate", "2027-09-22"))
                .andExpect(status().is3xxRedirection());
        wish = destinationWishRepository.findById(wish.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(DestinationWishStatus.PLANNED, wish.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(wish.getTripPlan());
        mockMvc.perform(get("/plans/{id}", wish.getTripPlan().getId()).session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString("TRIP COMMAND CENTER")));
    }

    @Test
    void tripWorkspaceStoresDailyActivitiesChecklistAndExportsCalendar() throws Exception {
        User user = createUser(true);
        TripPlan plan = createPlan(user, "行程工作台测试");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, user.getId());

        mockMvc.perform(post("/plans/{id}/activities", plan.getId()).with(csrf()).session(session)
                        .param("activityDate", "2026-08-03").param("startTime", "09:30")
                        .param("title", "西湖晨间骑行").param("location", "断桥")
                        .param("notes", "提前十分钟集合"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/plans/{id}/checklist", plan.getId()).with(csrf()).session(session)
                        .param("title", "确认高铁车票").param("category", TripChecklistCategory.TRANSPORT.name())
                        .param("assigneeId", user.getId().toString()))
                .andExpect(status().is3xxRedirection());
        org.junit.jupiter.api.Assertions.assertEquals(1,
                tripPlanActivityRepository.findByTripPlanIdOrderByActivityDateAscStartTimeAscCreatedAtAsc(plan.getId()).size());
        org.junit.jupiter.api.Assertions.assertEquals(1,
                tripChecklistItemRepository.findByTripPlanIdOrderByCompletedAscCreatedAtAsc(plan.getId()).size());

        mockMvc.perform(get("/plans/{id}", plan.getId()).session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString("西湖晨间骑行")))
                .andExpect(content().string(containsString("确认高铁车票")));
        mockMvc.perform(get("/plans/{id}/calendar.ics", plan.getId()).session(session))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andExpect(content().string(containsString("BEGIN:VCALENDAR")))
                .andExpect(content().string(containsString("西湖晨间骑行")));
    }

    @Test
    void outsiderCannotAddWorkspaceItemsOrDownloadCalendar() throws Exception {
        User owner = createUser(true);
        User outsider = createUser(true);
        TripPlan plan = createPlan(owner, "工作台越权测试");
        MockHttpSession outsiderSession = new MockHttpSession();
        outsiderSession.setAttribute(CurrentUserService.SESSION_USER_ID, outsider.getId());

        mockMvc.perform(post("/plans/{id}/activities", plan.getId()).with(csrf()).session(outsiderSession)
                        .param("activityDate", "2026-08-03").param("title", "越权安排"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/plans"));
        org.junit.jupiter.api.Assertions.assertTrue(
                tripPlanActivityRepository.findByTripPlanIdOrderByActivityDateAscStartTimeAscCreatedAtAsc(plan.getId()).isEmpty());
        mockMvc.perform(get("/plans/{id}/calendar.ics", plan.getId()).session(outsiderSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void readonlyShareIsOwnerControlledAndHidesPrivatePlanFields() throws Exception {
        User owner = createUser(true);
        User outsider = createUser(true);
        TripPlan plan = createPlan(owner, "只读分享测试");
        plan.setBudget(new java.math.BigDecimal("9999.00"));
        plan.setNotes("PRIVATE-PLAN-NOTE");
        tripPlanRepository.saveAndFlush(plan);
        MockHttpSession ownerSession = new MockHttpSession();
        ownerSession.setAttribute(CurrentUserService.SESSION_USER_ID, owner.getId());
        MockHttpSession outsiderSession = new MockHttpSession();
        outsiderSession.setAttribute(CurrentUserService.SESSION_USER_ID, outsider.getId());

        mockMvc.perform(get("/shared/plans/not-enabled-token")).andExpect(status().isNotFound());
        mockMvc.perform(post("/plans/{id}/share", plan.getId()).with(csrf()).session(ownerSession)
                        .param("enabled", "true"))
                .andExpect(status().is3xxRedirection());
        TripPlan shared = tripPlanRepository.findById(plan.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(shared.isShareEnabled());
        org.junit.jupiter.api.Assertions.assertEquals(64, shared.getShareToken().length());

        mockMvc.perform(get("/shared/plans/{token}", shared.getShareToken()))
                .andExpect(status().isOk()).andExpect(content().string(containsString(shared.getTitle())))
                .andExpect(content().string(not(containsString("PRIVATE-PLAN-NOTE"))))
                .andExpect(content().string(not(containsString("9999.00"))));
        mockMvc.perform(post("/plans/{id}/share", plan.getId()).with(csrf()).session(outsiderSession)
                        .param("enabled", "false"))
                .andExpect(status().is3xxRedirection());
        org.junit.jupiter.api.Assertions.assertTrue(tripPlanRepository.findById(plan.getId()).orElseThrow().isShareEnabled());
        mockMvc.perform(post("/plans/{id}/share", plan.getId()).with(csrf()).session(ownerSession)
                        .param("enabled", "false"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/shared/plans/{token}", shared.getShareToken())).andExpect(status().isNotFound());
    }

    @Test
    void destinationGuideUsesOnlyApprovedPublicFootprints() throws Exception {
        User user = createUser(true);
        String publicMarker = "公开攻略-" + UUID.randomUUID().toString().substring(0, 8);
        String privateMarker = "私密攻略-" + UUID.randomUUID().toString().substring(0, 8);
        createPost(user, ContentReviewStatus.APPROVED, publicMarker, "浙江 杭州 西湖", "浙江", "城市漫游", LocalDate.now());
        TravelPost privatePost = createPost(user, ContentReviewStatus.APPROVED, privateMarker,
                "浙江 杭州 灵隐寺", "浙江", "人文历史", LocalDate.now());
        privatePost.setVisibility(PostVisibility.PRIVATE);
        postRepository.saveAndFlush(privatePost);

        mockMvc.perform(get("/guides").param("province", "浙江"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(publicMarker)))
                .andExpect(content().string(not(containsString(privateMarker))));
    }

    @Test
    void smartSuggestionsRequireServerValidatedKeysAndPreserveExistingActivities() throws Exception {
        User owner = createUser(true);
        createPost(owner, ContentReviewStatus.APPROVED, "杭州灵感路线", "浙江 杭州 西湖", "浙江", "城市漫游", LocalDate.now());
        TripPlan plan = createPlan(owner, "智能行程测试");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, owner.getId());

        mockMvc.perform(post("/plans/{id}/suggestions/apply", plan.getId()).with(csrf()).session(session)
                        .param("suggestionKeys", "tampered-key"))
                .andExpect(status().is3xxRedirection());
        org.junit.jupiter.api.Assertions.assertTrue(tripPlanActivityRepository
                .findByTripPlanIdOrderByActivityDateAscStartTimeAscCreatedAtAsc(plan.getId()).isEmpty());

        SmartItineraryService.Suggestion suggestion = smartItineraryService.suggest(plan, java.util.List.of()).get(0);
        mockMvc.perform(post("/plans/{id}/suggestions/apply", plan.getId()).with(csrf()).session(session)
                        .param("suggestionKeys", suggestion.key()))
                .andExpect(status().is3xxRedirection());
        var activities = tripPlanActivityRepository
                .findByTripPlanIdOrderByActivityDateAscStartTimeAscCreatedAtAsc(plan.getId());
        org.junit.jupiter.api.Assertions.assertEquals(1, activities.size());
        org.junit.jupiter.api.Assertions.assertEquals(suggestion.location(), activities.get(0).getLocation());

        mockMvc.perform(post("/plans/{id}/suggestions/apply", plan.getId()).with(csrf()).session(session)
                        .param("suggestionKeys", suggestion.key()))
                .andExpect(status().is3xxRedirection());
        org.junit.jupiter.api.Assertions.assertEquals(1, tripPlanActivityRepository
                .findByTripPlanIdOrderByActivityDateAscStartTimeAscCreatedAtAsc(plan.getId()).size());
    }

    @Test
    void readinessScoreRisesWhenCorePreparationIsAdded() {
        User owner = createUser(true);
        TripPlan plan = createPlan(owner, "准备度测试");
        int initial = tripReadinessService.evaluate(plan, java.util.List.of(), java.util.List.of()).score();
        plan.setBudget(new java.math.BigDecimal("5000"));
        plan.setNotes("高铁出行，提前半小时集合");
        com.example.travelfootprint.model.TripPlanActivity activity = new com.example.travelfootprint.model.TripPlanActivity();
        activity.setTitle("西湖漫游");
        com.example.travelfootprint.model.TripChecklistItem checklist = new com.example.travelfootprint.model.TripChecklistItem();
        checklist.setCategory(TripChecklistCategory.TRANSPORT);
        checklist.setCompleted(true);
        int improved = tripReadinessService.evaluate(plan, java.util.List.of(activity), java.util.List.of(checklist)).score();
        org.junit.jupiter.api.Assertions.assertTrue(improved > initial);
    }

    @Test
    void globalSearchRespectsPostPrivacyAndCollaborativePlanAccess() throws Exception {
        User owner = createUser(true);
        User outsider = createUser(true);
        String publicMarker = "全局公开搜索-" + UUID.randomUUID().toString().substring(0, 8);
        String privateMarker = "全局私密搜索-" + UUID.randomUUID().toString().substring(0, 8);
        TravelPost publicPost = createPost(owner, ContentReviewStatus.APPROVED, publicMarker, "浙江 杭州 西湖", "浙江", "城市漫游", LocalDate.now());
        TravelPost privatePost = createPost(owner, ContentReviewStatus.APPROVED, privateMarker,
                "浙江 杭州 私藏地点", "浙江", "城市漫游", LocalDate.now());
        privatePost.setVisibility(PostVisibility.PRIVATE);
        postRepository.saveAndFlush(privatePost);
        TripPlan plan = createPlan(owner, "仅参与者计划搜索");
        MockHttpSession ownerSession = new MockHttpSession();
        ownerSession.setAttribute(CurrentUserService.SESSION_USER_ID, owner.getId());
        MockHttpSession outsiderSession = new MockHttpSession();
        outsiderSession.setAttribute(CurrentUserService.SESSION_USER_ID, outsider.getId());

        mockMvc.perform(get("/search").param("q", publicMarker))
                .andExpect(status().isOk()).andExpect(content().string(containsString("/posts/" + publicPost.getId())));
        mockMvc.perform(get("/search").param("q", privateMarker))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("/posts/" + privatePost.getId()))));
        mockMvc.perform(get("/search").session(ownerSession).param("q", privateMarker))
                .andExpect(status().isOk()).andExpect(content().string(containsString("/posts/" + privatePost.getId())));
        mockMvc.perform(get("/search").session(outsiderSession).param("q", plan.getTitle()))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("/plans/" + plan.getId()))));
        mockMvc.perform(get("/search").session(ownerSession).param("q", plan.getTitle()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("/plans/" + plan.getId())));
    }

    @Test
    void personalizedRecommendationsExcludePrivateContentAndHonorDismissal() throws Exception {
        User viewer = createUser(true);
        User author = createUser(true);
        createPost(viewer, ContentReviewStatus.APPROVED, "我的自然偏好", "浙江 杭州", "浙江", "自然风光", LocalDate.now());
        String publicMarker = "推荐公开候选-" + UUID.randomUUID().toString().substring(0, 8);
        String privateMarker = "推荐私密候选-" + UUID.randomUUID().toString().substring(0, 8);
        TravelPost publicPost = createPost(author, ContentReviewStatus.APPROVED, publicMarker,
                "浙江 湖州 莫干山", "浙江", "自然风光", LocalDate.now());
        TravelPost privatePost = createPost(author, ContentReviewStatus.APPROVED, privateMarker,
                "浙江 私密山谷", "浙江", "自然风光", LocalDate.now());
        privatePost.setVisibility(PostVisibility.PRIVATE);
        postRepository.saveAndFlush(privatePost);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, viewer.getId());

        mockMvc.perform(get("/recommendations").session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString(publicMarker)))
                .andExpect(content().string(not(containsString(privateMarker))));
        mockMvc.perform(post("/recommendations/{id}/dismiss", publicPost.getId()).with(csrf()).session(session))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/recommendations"));
        org.junit.jupiter.api.Assertions.assertTrue(
                recommendationDismissalRepository.existsByUserIdAndPostId(viewer.getId(), publicPost.getId()));
        mockMvc.perform(get("/recommendations").session(session))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString(publicMarker))));
        mockMvc.perform(post("/recommendations/reset").with(csrf()).session(session))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/recommendations"));
        org.junit.jupiter.api.Assertions.assertFalse(
                recommendationDismissalRepository.existsByUserIdAndPostId(viewer.getId(), publicPost.getId()));
        mockMvc.perform(get("/recommendations").session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString(publicMarker)));
    }

    @Test
    void advancedInsightsArePrivateAndUsePersonalFootprints() throws Exception {
        User user = createUser(true);
        createPost(user, ContentReviewStatus.APPROVED, "洞察自然足迹", "四川 成都", "四川", "自然风光", LocalDate.now());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUserService.SESSION_USER_ID, user.getId());

        mockMvc.perform(get("/insights"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mockMvc.perform(get("/insights").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("你的旅行画像，正在形成")))
                .andExpect(content().string(containsString("自然风光")));
    }

    @Test
    void securityHeadersHealthAndSafeErrorPageAreAvailable() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("object-src 'none'")))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(header().exists("Server-Timing"));
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"UP\"")))
                .andExpect(content().string(containsString("\"database\":true")))
                .andExpect(content().string(containsString("\"storage\":true")));
        mockMvc.perform(get("/route-that-does-not-exist-" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("这条旅行路线暂时不存在")))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void repeatedLoginFailuresTemporarilyBlockTheSameAccountAndAddress() throws Exception {
        User user = createUser(true);
        String remoteAddress = "198.51.100." + (10 + user.getId() % 100);
        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(post("/login").with(csrf()).with(request -> {
                        request.setRemoteAddr(remoteAddress);
                        return request;
                    }).param("username", user.getUsername()).param("password", "wrong-password"))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        }
        mockMvc.perform(post("/login").with(csrf()).with(request -> {
                    request.setRemoteAddr(remoteAddress);
                    return request;
                }).param("username", user.getUsername()).param("password", "123456"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    void directUploadAccessUsesTheSamePostPrivacyRules() throws Exception {
        User owner = createUser(true);
        TravelPost post = createPost(owner, ContentReviewStatus.APPROVED);
        post.setVisibility(PostVisibility.PRIVATE);
        String filename = "security-test-" + UUID.randomUUID() + ".png";
        String publicPath = "/uploads/" + filename;
        Path file = Path.of("uploads", filename).toAbsolutePath().normalize();
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        post.setPhotoPath(publicPath);
        postRepository.saveAndFlush(post);
        MockHttpSession ownerSession = new MockHttpSession();
        ownerSession.setAttribute(CurrentUserService.SESSION_USER_ID, owner.getId());
        try {
            mockMvc.perform(get(publicPath)).andExpect(status().isNotFound());
            mockMvc.perform(get(publicPath).session(ownerSession))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", containsString("no-store")));
            post.setVisibility(PostVisibility.PUBLIC);
            postRepository.saveAndFlush(post);
            mockMvc.perform(get(publicPath))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", containsString("max-age")));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private User createUser(boolean enabled) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User user = new User();
        user.setUsername("test_" + suffix);
        user.setNickname("测试用户" + suffix);
        user.setPasswordHash(passwordEncoder.encode("123456"));
        user.setBio("");
        user.setAdmin(false);
        user.setEnabled(enabled);
        return userRepository.saveAndFlush(user);
    }

    private TripPlan createPlan(User user, String title) {
        TripPlan plan = new TripPlan();
        plan.setOwner(user);
        plan.setTitle(title + UUID.randomUUID().toString().substring(0, 6));
        plan.setDestination("浙江 杭州");
        plan.setStartDate(LocalDate.of(2026, 8, 1));
        plan.setEndDate(LocalDate.of(2026, 8, 10));
        plan.setStatus(TripPlanStatus.PLANNED);
        plan.setNotes("");
        return tripPlanRepository.saveAndFlush(plan);
    }

    private TravelPost createPost(User user, ContentReviewStatus reviewStatus) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return createPost(
                user,
                reviewStatus,
                "测试足迹-" + suffix,
                "浙江 杭州 西湖",
                "浙江",
                "自然风光",
                LocalDate.of(2026, 8, 1));
    }

    private TravelPost createPost(
            User user,
            ContentReviewStatus reviewStatus,
            String title,
            String location,
            String province,
            String category,
            LocalDate travelDate) {
        TravelPost post = new TravelPost();
        post.setAuthor(user);
        post.setTitle(title);
        post.setLocation(location);
        post.setProvince(province);
        post.setContent("用于验证审核可见性与编辑流程的测试内容。");
        post.setCategory(category);
        post.setTags("测试");
        post.setTravelDate(travelDate);
        post.setReviewStatus(reviewStatus);
        return postRepository.saveAndFlush(post);
    }
}

package com.example.travelfootprint;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.travelfootprint.model.ContentReviewStatus;
import com.example.travelfootprint.model.TravelPost;
import com.example.travelfootprint.model.User;
import com.example.travelfootprint.repository.TravelPostRepository;
import com.example.travelfootprint.repository.UserRepository;
import com.example.travelfootprint.repository.MiniAppSessionRepository;
import com.example.travelfootprint.service.MiniAppTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MiniAppFeatureTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TravelPostRepository postRepository;

    @Autowired
    private MiniAppTokenService tokenService;

    @Autowired
    private MiniAppSessionRepository sessionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void miniCatalogExposesPublishingCategories() throws Exception {
        mockMvc.perform(get("/api/mini/catalog/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("自然风光"));
    }

    @Test
    void miniForegroundArrivalReminderMatchesNearbyLocationAndRequiresLogin() throws Exception {
        User user = createUser();
        String token = tokenService.issueToken(user);

        mockMvc.perform(get("/api/mini/location/arrival-match")
                        .header("X-Mini-Token", token)
                        .param("longitude", "120.1551")
                        .param("latitude", "30.2741"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value("杭州 西湖"))
                .andExpect(jsonPath("$.province").value("浙江"))
                .andExpect(jsonPath("$.matched").value(true));

        mockMvc.perform(get("/api/mini/location/arrival-match")
                        .param("longitude", "120.1551")
                        .param("latitude", "30.2741"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void miniPersonalMapReturnsCoordinatesAndEnablesPublishOrderedRoute() throws Exception {
        User user = createUser();
        TravelPost post = new TravelPost();
        post.setAuthor(user);
        post.setTitle("小程序地图测试");
        post.setLocation("杭州西湖");
        post.setProvince("浙江");
        post.setCategory("自然风光");
        post.setContent("用于验证小程序原生地图点位。");
        post.setTravelDate(LocalDate.now());
        post.setLatitude(30.2741);
        post.setLongitude(120.1551);
        post.setReviewStatus(ContentReviewStatus.APPROVED);
        postRepository.saveAndFlush(post);
        TravelPost earlierPost = new TravelPost();
        earlierPost.setAuthor(user);
        earlierPost.setTitle("更早的旅行故事");
        earlierPost.setLocation("宁波东钱湖");
        earlierPost.setProvince("浙江");
        earlierPost.setCategory("自然风光");
        earlierPost.setContent("用于验证按真实旅行日期排序和地图视频故事。");
        earlierPost.setTravelDate(LocalDate.of(2025, 5, 1));
        earlierPost.setLatitude(29.79);
        earlierPost.setLongitude(121.66);
        earlierPost.setVideoPath("/uploads/mini-map-story.mp4");
        earlierPost.setReviewStatus(ContentReviewStatus.APPROVED);
        postRepository.saveAndFlush(earlierPost);
        String token = tokenService.issueToken(user);

        mockMvc.perform(get("/api/mini/map/overview")
                        .header("X-Mini-Token", token)
                        .param("mine", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routeEnabled").value(true))
                .andExpect(jsonPath("$.points[0].postId").value(earlierPost.getId()))
                .andExpect(jsonPath("$.points[0].travelDate").value("2025-05-01"))
                .andExpect(jsonPath("$.points[0].videoPath").value("/uploads/mini-map-story.mp4"))
                .andExpect(jsonPath("$.points[1].latitude").value(30.2741))
                .andExpect(jsonPath("$.points[1].longitude").value(120.1551));

        mockMvc.perform(get("/api/mini/map/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routeEnabled").value(false));
    }

    @Test
    void miniPlansAndThreePeriodReportAreAvailableToAuthenticatedUser() throws Exception {
        User user = createUser();
        String token = tokenService.issueToken(user);
        String response = mockMvc.perform(post("/api/mini/plans")
                        .header("X-Mini-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "周末杭州",
                                  "destination": "浙江杭州",
                                  "startDate": "2026-08-15",
                                  "endDate": "2026-08-16",
                                  "budget": "1200",
                                  "status": "PLANNED",
                                  "notes": "小程序创建"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusLabel").value("规划中"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode createdPlan = objectMapper.readTree(response);

        mockMvc.perform(get("/api/mini/plans").header("X-Mini-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("周末杭州"));

        for (String period : new String[] {"week", "month", "year"}) {
            mockMvc.perform(get("/api/mini/reports")
                            .header("X-Mini-Token", token)
                            .param("period", period))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.period").value(period));
        }

        mockMvc.perform(delete("/api/mini/plans/{id}", createdPlan.get("id").asLong())
                        .header("X-Mini-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void miniPostCanBePublishedWithoutAPhotoUsingFormEncoding() throws Exception {
        User user = createUser();
        String token = tokenService.issueToken(user);

        String response = mockMvc.perform(post("/api/mini/posts")
                        .header("X-Mini-Token", token)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("title", "不带照片的小程序足迹")
                        .param("location", "杭州西湖")
                        .param("province", "浙江")
                        .param("content", "验证小程序在没有选择图片时仍然可以发布。")
                        .param("travelDate", "2026-08-09")
                        .param("category", "自然风光")
                        .param("tags", "小程序,测试")
                        .param("latitude", "30.2741")
                        .param("longitude", "120.1551")
                        .param("visibility", "PRIVATE")
                        .param("approximateLocation", "true"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("不带照片的小程序足迹"))
                .andExpect(jsonPath("$.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.approximateLocation").value(true))
                .andReturn().getResponse().getContentAsString();
        long postId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(post("/api/mini/posts/{id}/privacy", postId)
                        .header("X-Mini-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"visibility":"FOLLOWERS","approximateLocation":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("FOLLOWERS"))
                .andExpect(jsonPath("$.approximateLocation").value(false));
    }

    @Test
    void miniPassportUsesRealFootprintsForBadgesStampsAndVideoCount() throws Exception {
        User user = createUser();
        TravelPost first = createPost(user, "杭州春日", "杭州西湖", LocalDate.of(2025, 3, 12));
        TravelPost second = createPost(user, "杭州再见", "杭州西湖", LocalDate.of(2026, 8, 1));
        second.setVideoPath("/uploads/passport-video.mp4");
        postRepository.saveAndFlush(second);
        String token = tokenService.issueToken(user);

        mockMvc.perform(get("/api/mini/passport").header("X-Mini-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postCount").value(2))
                .andExpect(jsonPath("$.provinceCount").value(1))
                .andExpect(jsonPath("$.videoCount").value(1))
                .andExpect(jsonPath("$.badges[1].name").value("动态旅行家"))
                .andExpect(jsonPath("$.badges[1].earned").value(true))
                .andExpect(jsonPath("$.stamps[?(@.province == '浙江')].visited")
                        .value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.milestones[0].postId").value(second.getId()))
                .andExpect(jsonPath("$.milestones[1].postId").value(first.getId()));

        mockMvc.perform(get("/api/mini/passport"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void miniPostSupportsVideoUploadPlaybackReplacementAndRemoval() throws Exception {
        User user = createUser();
        String token = tokenService.issueToken(user);
        MockMultipartFile mp4 = new MockMultipartFile(
                "video", "journey.mp4", "video/mp4",
                new byte[] {0x00, 0x00, 0x00, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'});

        String createResponse = mockMvc.perform(multipart("/api/mini/posts")
                        .file(mp4)
                        .header("X-Mini-Token", token)
                        .param("title", "小程序视频足迹")
                        .param("location", "杭州西湖")
                        .param("province", "浙江")
                        .param("content", "验证小程序视频上传与鉴权播放。")
                        .param("travelDate", "2026-08-17")
                        .param("category", "自然风光")
                        .param("tags", "小程序,视频"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.videoPath").isNotEmpty())
                .andExpect(jsonPath("$.owned").value(true))
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(createResponse);
        long postId = created.get("id").asLong();
        String firstVideoPath = created.get("videoPath").asText();

        mockMvc.perform(get(firstVideoPath)).andExpect(status().isNotFound());
        mockMvc.perform(get(firstVideoPath).param("miniToken", token))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Type", "video/mp4"));

        MockMultipartFile webm = new MockMultipartFile(
                "video", "replacement.webm", "video/webm",
                new byte[] {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3});
        String replacementResponse = mockMvc.perform(multipart("/api/mini/posts/{id}/video", postId)
                        .file(webm)
                        .header("X-Mini-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoPath").value(org.hamcrest.Matchers.endsWith(".webm")))
                .andReturn().getResponse().getContentAsString();
        String replacementPath = objectMapper.readTree(replacementResponse).get("videoPath").asText();
        mockMvc.perform(get(firstVideoPath).param("miniToken", token)).andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/mini/posts/{id}/video", postId)
                        .header("X-Mini-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videoPath").doesNotExist());
        mockMvc.perform(get(replacementPath).param("miniToken", token)).andExpect(status().isNotFound());
    }

    @Test
    void miniLoginTokenRemainsValidWhenTokenServiceIsRecreated() {
        User user = createUser();
        String token = tokenService.issueToken(user);

        MiniAppTokenService restartedService = new MiniAppTokenService(sessionRepository, userRepository, 30);

        assertEquals(user.getId(), restartedService.findUser(token).orElseThrow().getId());
        assertEquals(1L, sessionRepository.count());
    }

    private User createUser() {
        String marker = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = new User();
        user.setUsername("mini_" + marker);
        user.setNickname("小程序用户" + marker);
        user.setPasswordHash("test-password-hash");
        user.setBio("");
        user.setEnabled(true);
        user.setAdmin(false);
        return userRepository.saveAndFlush(user);
    }

    private TravelPost createPost(User user, String title, String location, LocalDate travelDate) {
        TravelPost post = new TravelPost();
        post.setAuthor(user);
        post.setTitle(title);
        post.setLocation(location);
        post.setProvince("浙江");
        post.setCategory("自然风光");
        post.setContent("小程序旅行护照测试内容。");
        post.setTravelDate(travelDate);
        post.setReviewStatus(ContentReviewStatus.APPROVED);
        return postRepository.saveAndFlush(post);
    }
}

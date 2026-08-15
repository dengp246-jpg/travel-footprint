package com.example.travelfootprint;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        String token = tokenService.issueToken(user);

        mockMvc.perform(get("/api/mini/map/overview")
                        .header("X-Mini-Token", token)
                        .param("mine", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routeEnabled").value(true))
                .andExpect(jsonPath("$.points[0].latitude").value(30.2741))
                .andExpect(jsonPath("$.points[0].longitude").value(120.1551));

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

        mockMvc.perform(post("/api/mini/posts")
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
                        .param("longitude", "120.1551"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("不带照片的小程序足迹"));
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
}

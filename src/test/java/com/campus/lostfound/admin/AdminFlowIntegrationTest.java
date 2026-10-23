package com.campus.lostfound.admin;

import com.campus.lostfound.auth.service.TokenRevocationService;
import com.campus.lostfound.chat.websocket.ChatSessionRegistry;
import com.campus.lostfound.user.entity.SysUser;
import com.campus.lostfound.user.enums.UserRole;
import com.campus.lostfound.user.enums.UserStatus;
import com.campus.lostfound.user.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员用户管理、帖子处置、统计和审计日志集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private TokenRevocationService tokenRevocationService;

    @MockitoBean
    private ChatSessionRegistry chatSessionRegistry;

    @Test
    void rejectsNormalUserAndAllowsAdminQueries() throws Exception {
        Session user = registerAndLogin("admin_denied");
        Session admin = createAdminAndLogin("admin_query");

        mockMvc.perform(get("/admin/statistics")
                        .header("Authorization", user.authorization()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("仅管理员可执行该操作"));

        mockMvc.perform(get("/admin/users")
                        .header("Authorization", admin.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());

        mockMvc.perform(get("/admin/statistics")
                        .header("Authorization", admin.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").isNumber())
                .andExpect(jsonPath("$.data.totalPosts").isNumber());
    }

    @Test
    void disablesAndEnablesUsersWithAuditAndImmediateRevocation() throws Exception {
        Session admin = createAdminAndLogin("admin_status");
        Session target = registerAndLogin("status_user");

        mockMvc.perform(patch("/admin/users/{id}/status", target.userId())
                        .header("Authorization", admin.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "DISABLED",
                                  "reason": "多次发布违规内容"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        verify(tokenRevocationService).invalidateAllForUser(target.userId());
        verify(chatSessionRegistry).closeUser(target.userId());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "Campus2026"
                                }
                                """.formatted(target.username())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("账号已被禁用"));

        mockMvc.perform(patch("/admin/users/{id}/status", target.userId())
                        .header("Authorization", admin.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\",\"reason\":\"申诉通过\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "Campus2026"
                                }
                                """.formatted(target.username())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        mockMvc.perform(get("/admin/operation-logs")
                        .queryParam("targetType", "USER")
                        .queryParam("targetId", String.valueOf(target.userId()))
                        .header("Authorization", admin.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].action").value("USER_STATUS_CHANGE"));
    }

    @Test
    void preventsAdminFromDisablingOwnAccount() throws Exception {
        Session admin = createAdminAndLogin("admin_self");

        mockMvc.perform(patch("/admin/users/{id}/status", admin.userId())
                        .header("Authorization", admin.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("不能禁用当前登录的管理员账号"));

        mockMvc.perform(get("/admin/operation-logs")
                        .queryParam("targetType", "USER")
                        .queryParam("targetId", String.valueOf(admin.userId()))
                        .queryParam("result", "FAILED")
                        .header("Authorization", admin.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].detail.error")
                        .value("不能禁用当前登录的管理员账号"));
    }

    @Test
    void hidesAndRestoresPostWhileRecordingAudit() throws Exception {
        Session owner = registerAndLogin("moderate_owner");
        Session applicant = registerAndLogin("moderate_user");
        Session admin = createAdminAndLogin("admin_moderate");
        String marker = "moderate" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8);
        long postId = createPost(owner, marker);

        mockMvc.perform(get("/posts/{id}", postId))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/admin/posts/{id}/moderation", postId)
                        .header("Authorization", admin.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"HIDE\",\"reason\":\"包含违规联系方式\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));

        mockMvc.perform(get("/posts/{id}", postId))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/posts/{postId}/applications", postId)
                        .header("Authorization", applicant.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"隐藏期间不应接受新的申请\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("当前帖子状态不接受新申请"));

        mockMvc.perform(patch("/admin/posts/{id}/moderation", postId)
                        .header("Authorization", admin.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"RESTORE\",\"reason\":\"申诉通过\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(get("/posts/{id}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(get("/admin/operation-logs")
                        .queryParam("targetType", "POST")
                        .queryParam("targetId", String.valueOf(postId))
                        .header("Authorization", admin.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.records[0].detail.reason").isNotEmpty());
    }

    @Test
    void listsPostsApplicationsAndStatistics() throws Exception {
        Session owner = registerAndLogin("admin_list_owner");
        Session applicant = registerAndLogin("admin_list_user");
        Session admin = createAdminAndLogin("admin_list");
        String marker = "list" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8);
        long postId = createPost(owner, marker);

        mockMvc.perform(post("/posts/{postId}/applications", postId)
                        .header("Authorization", applicant.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"管理员申请列表联调数据\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/admin/posts")
                        .queryParam("keyword", marker)
                        .queryParam("status", "PUBLISHED")
                        .header("Authorization", admin.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(postId));

        mockMvc.perform(get("/admin/applications")
                        .queryParam("postId", String.valueOf(postId))
                        .queryParam("applicationType", "CLAIM")
                        .header("Authorization", admin.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].applicationType").value("CLAIM"));

        String statisticsResponse = mockMvc.perform(get("/admin/statistics")
                        .header("Authorization", admin.authorization()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode statistics = objectMapper.readTree(statisticsResponse).path("data");
        assertThat(statistics.path("totalUsers").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(statistics.path("totalPosts").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(statistics.path("totalApplications").asLong()).isGreaterThanOrEqualTo(1);
    }

    private long createPost(Session owner, String marker) throws Exception {
        String response = mockMvc.perform(post("/posts")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postType": "FOUND",
                                  "title": "%s 校园卡",
                                  "description": "用于管理员模块集成测试",
                                  "category": "证件卡片",
                                  "location": "图书馆",
                                  "occurrenceTime": "2026-10-23T10:30:00",
                                  "contactText": "值班室领取",
                                  "contactVisibility": "ACCEPTED_ONLY",
                                  "imageUrls": []
                                }
                                """.formatted(marker)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private Session registerAndLogin(String prefix) throws Exception {
        String username = safePrefix(prefix) + "_" + uniqueSuffix();
        String password = "Campus2026";
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s",
                                  "nickname": "%s"
                                }
                                """.formatted(username, password, prefix)))
                .andExpect(status().isCreated());
        return login(username, password);
    }

    private Session createAdminAndLogin(String prefix) throws Exception {
        String username = safePrefix(prefix) + "_" + uniqueSuffix();
        String password = "Admin2026";
        LocalDateTime now = LocalDateTime.now();
        userMapper.insert(SysUser.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .nickname(prefix)
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
        return login(username, password);
    }

    private Session login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(response).path("data");
        return new Session(
                data.path("user").path("username").asText(),
                "Bearer " + data.path("accessToken").asText(),
                data.path("user").path("id").asLong()
        );
    }

    private String safePrefix(String prefix) {
        return prefix.substring(0, Math.min(prefix.length(), 10));
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private record Session(String username, String authorization, long userId) {
    }
}

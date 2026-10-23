package com.campus.lostfound.application;

import com.campus.lostfound.auth.service.TokenRevocationService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认领/线索申请的完整业务闭环测试。
 *
 * <p>覆盖申请类型推导、重复提交保护、发布者/管理员权限、接受后的联系方式可见性、
 * 自动拒绝其他申请、撤回后重新申请，以及完成请求的双确认流程。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationFlowIntegrationTest {

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

    @Test
    void completesClaimFlowAndProtectsContactAndPermissions() throws Exception {
        Session owner = registerAndLogin("claim_owner");
        Session applicant = registerAndLogin("claim_user");
        Session other = registerAndLogin("claim_other");
        long postId = createPost(owner);

        // FOUND 帖子应自动生成 CLAIM 申请，并进入 PENDING。
        long applicationId = createApplication(applicant, postId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.applicationType").value("CLAIM"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturnId();

        // 同一用户不能同时拥有第二条有效申请。
        createApplication(applicant, postId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("你已提交过该帖子的有效申请"));

        // 申请列表只允许发布者或管理员查看。
        mockMvc.perform(get("/posts/{postId}/applications", postId)
                        .header("Authorization", other.authorization()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/posts/{postId}/applications", postId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(applicationId));

        // 接受前，ACCEPTED_ONLY 联系方式对普通申请人也不可见。
        mockMvc.perform(get("/posts/{id}", postId)
                        .header("Authorization", applicant.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactText").doesNotExist());

        decide(applicant, applicationId, "ACCEPT")
                .andExpect(status().isForbidden());

        // 帖子还不是 PROCESSING，不能绕过申请接受直接完成。
        mockMvc.perform(patch("/posts/{id}/status", postId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"RESOLVED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("只有处理中的帖子可以确认完成"));

        // 发布者接受后，帖子进入 PROCESSING。
        decide(owner, applicationId, "ACCEPT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.postStatus").value("PROCESSING"));

        // 被接受申请人现在可以读取联系方式，其他登录用户仍不可见。
        mockMvc.perform(get("/posts/{id}", postId)
                        .header("Authorization", applicant.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactText").value("请到值班室认领"));

        mockMvc.perform(get("/posts/{id}", postId)
                        .header("Authorization", other.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactText").doesNotExist());

        createApplication(other, postId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("当前帖子状态不接受新申请"));

        // 申请人尚未发起完成请求，发布者不能提前确认完成。
        mockMvc.perform(patch("/posts/{id}/status", postId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"RESOLVED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("申请人尚未发起完成请求"));

        // 只有被接受的申请人本人可以发起完成请求。
        mockMvc.perform(patch("/applications/{id}/completion-request", applicationId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/applications/{id}/completion-request", applicationId)
                        .header("Authorization", applicant.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completionRequestedAt").isNotEmpty());

        // 双方条件都满足后，发布者最终确认 RESOLVED。
        mockMvc.perform(patch("/posts/{id}/status", postId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"RESOLVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.resolvedAt").isNotEmpty());

        mockMvc.perform(get("/posts/{postId}/applications", postId)
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.records[0].postStatus").value("RESOLVED"));
    }

    @Test
    void supportsWithdrawRejectAndNewApplicationHistory() throws Exception {
        Session owner = registerAndLogin("history_owner");
        Session applicant = registerAndLogin("history_user");
        Session other = registerAndLogin("history_other");
        long postId = createPost(owner, "LOST");

        // LOST 帖子对应 CLUE 申请。
        long firstId = createApplication(applicant, postId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.applicationType").value("CLUE"))
                .andReturnId();

        // 撤回后可以新增一条申请，同时保留旧记录作为历史。
        mockMvc.perform(patch("/applications/{id}/withdraw", firstId)
                        .header("Authorization", other.authorization()))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/applications/{id}/withdraw", firstId)
                        .header("Authorization", applicant.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));

        long secondId = createApplication(applicant, postId)
                .andExpect(status().isCreated())
                .andReturnId();
        org.assertj.core.api.Assertions.assertThat(secondId).isNotEqualTo(firstId);

        // 被拒绝的申请不能再次处理，但用户可以重新申请。
        decide(owner, secondId, "REJECT", "线索描述不够具体")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.processNote").value("线索描述不够具体"));

        decide(owner, secondId, "REJECT")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("申请已处理，不能重复操作"));

        createApplication(applicant, postId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(get("/users/me/applications")
                        .header("Authorization", applicant.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records.length()").value(3));

        mockMvc.perform(get("/users/me/applications")
                        .queryParam("status", "REJECTED")
                        .header("Authorization", applicant.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void acceptingOneApplicationRejectsOtherPendingApplicationsAsAdmin() throws Exception {
        Session owner = registerAndLogin("race_owner");
        Session firstApplicant = registerAndLogin("race_first");
        Session secondApplicant = registerAndLogin("race_second");
        Session admin = createAdminAndLogin("race_admin");
        long postId = createPost(owner);

        long firstId = createApplication(firstApplicant, postId)
                .andExpect(status().isCreated())
                .andReturnId();
        long secondId = createApplication(secondApplicant, postId)
                .andExpect(status().isCreated())
                .andReturnId();

        // 管理员接受一条申请后，另一条 PENDING 申请应自动变为 REJECTED。
        decide(admin, firstId, "ACCEPT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.processedBy.id").value(admin.userId()));

        mockMvc.perform(get("/users/me/applications")
                        .header("Authorization", secondApplicant.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(secondId))
                .andExpect(jsonPath("$.data.records[0].status").value("REJECTED"));
    }

    @Test
    void closingPostRejectsPendingApplications() throws Exception {
        Session owner = registerAndLogin("close_owner");
        Session applicant = registerAndLogin("close_user");
        long postId = createPost(owner);

        createApplication(applicant, postId)
                .andExpect(status().isCreated());

        // 关闭帖子时，未处理申请统一拒绝，但不物理删除。
        mockMvc.perform(patch("/posts/{id}/status", postId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"CLOSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        mockMvc.perform(get("/users/me/applications")
                        .header("Authorization", applicant.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.data.records[0].processNote")
                        .value("帖子已由发布者关闭"));

        createApplication(applicant, postId)
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsSelfApplicationAndInvalidDescription() throws Exception {
        Session owner = registerAndLogin("self_owner");
        Session applicant = registerAndLogin("self_user");
        long postId = createPost(owner);

        createApplication(owner, postId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("不能申请自己发布的帖子"));

        mockMvc.perform(post("/posts/{postId}/applications", postId)
                        .header("Authorization", applicant.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"太短\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void preventsChangingPostTypeAfterApplicationsExist() throws Exception {
        Session owner = registerAndLogin("type_owner");
        Session applicant = registerAndLogin("type_user");
        long postId = createPost(owner);

        createApplication(applicant, postId)
                .andExpect(status().isCreated());

        mockMvc.perform(put("/posts/{id}", postId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postType": "LOST",
                                  "title": "图书馆捡到校园卡",
                                  "description": "尝试修改帖子类型",
                                  "category": "证件卡片",
                                  "location": "图书馆一楼",
                                  "occurrenceTime": "2026-10-23T10:30:00",
                                  "contactText": "请到值班室认领",
                                  "contactVisibility": "ACCEPTED_ONLY",
                                  "imageUrls": []
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("已有申请的帖子不能修改帖子类型"));
    }

    private RequestResult createApplication(Session session, long postId) throws Exception {
        var result = mockMvc.perform(post("/posts/{postId}/applications", postId)
                .header("Authorization", session.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "description": "我可以提供物品细节和拾取经过作为证明"
                        }
                        """));
        return new RequestResult(result);
    }

    private RequestResult decide(
            Session session, long applicationId, String decision) throws Exception {
        return decide(session, applicationId, decision, null);
    }

    private RequestResult decide(
            Session session, long applicationId, String decision, String note) throws Exception {
        String body = note == null
                ? "{\"decision\":\"%s\"}".formatted(decision)
                : "{\"decision\":\"%s\",\"processNote\":\"%s\"}".formatted(decision, note);
        var result = mockMvc.perform(patch("/applications/{id}/decision", applicationId)
                .header("Authorization", session.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
        return new RequestResult(result);
    }

    private long createPost(Session owner) throws Exception {
        return createPost(owner, "FOUND");
    }

    private long createPost(Session owner, String postType) throws Exception {
        String title = postType.equals("FOUND") ? "图书馆捡到校园卡" : "丢失一个黑色钱包";
        String body = """
                {
                  "postType": "%s",
                  "title": "%s",
                  "description": "物品信息用于测试申请流程",
                  "category": "证件卡片",
                  "location": "图书馆一楼",
                  "occurrenceTime": "2026-10-23T10:30:00",
                  "contactText": "请到值班室认领",
                  "contactVisibility": "ACCEPTED_ONLY",
                  "imageUrls": []
                }
                """.formatted(postType, title);
        String response = mockMvc.perform(post("/posts")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private Session registerAndLogin(String prefix) throws Exception {
        String username = safePrefix(prefix) + "_" + uniqueSuffix();
        String password = "Campus2026";
        register(username, password, prefix);
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

    private void register(String username, String password, String nickname) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s",
                                  "nickname": "%s"
                                }
                                """.formatted(username, password, nickname)))
                .andExpect(status().isCreated());
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
        JsonNode json = objectMapper.readTree(response).path("data");
        return new Session(
                "Bearer " + json.path("accessToken").asText(),
                json.path("user").path("id").asLong());
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String safePrefix(String prefix) {
        return prefix.substring(0, Math.min(prefix.length(), 10));
    }

    private record Session(String authorization, long userId) {
    }

    private record RequestResult(
            org.springframework.test.web.servlet.ResultActions actions) {

        RequestResult andExpect(
                org.springframework.test.web.servlet.ResultMatcher... matchers)
                throws Exception {
            for (org.springframework.test.web.servlet.ResultMatcher matcher : matchers) {
                actions.andExpect(matcher);
            }
            return this;
        }

        long andReturnId() throws Exception {
            String response = actions.andReturn().getResponse().getContentAsString();
            return new ObjectMapper().readTree(response).path("data").path("id").asLong();
        }
    }
}

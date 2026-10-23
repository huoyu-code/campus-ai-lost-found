package com.campus.lostfound.chat;

import com.campus.lostfound.auth.service.TokenRevocationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST 聊天历史、会话权限、消息幂等和持久化通知集成测试。
 *
 * <p>WebSocket 实时链路由独立测试覆盖；本类验证即使实时连接不可用，
 * REST 仍能完成发送、历史查询和通知读取。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatNotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TokenRevocationService tokenRevocationService;

    @Test
    void createsConversationPersistsIdempotentMessageAndManagesNotifications()
            throws Exception {
        Session owner = registerAndLogin("chat_owner");
        Session applicant = registerAndLogin("chat_user");
        Session outsider = registerAndLogin("chat_other");
        long postId = createPost(owner);
        long applicationId = createApplication(applicant, postId);

        // 创建申请后，发布者收到持久化通知，但通知接口只对本人开放。
        mockMvc.perform(get("/notifications")
                        .queryParam("type", "APPLICATION_RECEIVED")
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].businessId").value(applicationId))
                .andExpect(jsonPath("$.data.records[0].read").value(false));

        // 没有申请关系的第三方不能与发布者创建会话。
        mockMvc.perform(post("/conversations")
                        .header("Authorization", outsider.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conversationBody(postId, owner.userId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("只有该帖发布者和申请人可以创建会话"));

        String conversationResponse = mockMvc.perform(post("/conversations")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conversationBody(postId, applicant.userId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.postId").value(postId))
                .andExpect(jsonPath("$.data.peer.id").value(applicant.userId()))
                .andReturn().getResponse().getContentAsString();
        long conversationId = objectMapper.readTree(conversationResponse)
                .path("data").path("id").asLong();

        // 申请人以相同帖子和发布者创建时，返回同一个会话。
        mockMvc.perform(post("/conversations")
                        .header("Authorization", applicant.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conversationBody(postId, owner.userId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(conversationId));

        mockMvc.perform(get("/conversations")
                        .header("Authorization", applicant.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].peer.id").value(owner.userId()));

        mockMvc.perform(get("/conversations/{id}/messages", conversationId)
                        .header("Authorization", outsider.authorization()))
                .andExpect(status().isNotFound());

        String requestId = "rest_" + randomSuffix();
        String createdMessageResponse = sendMessage(
                owner, conversationId, requestId, "你好，请描述物品特征")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value("你好，请描述物品特征"))
                .andReturn().getResponse()
                .getContentAsString();
        long messageId = readId(createdMessageResponse, "data");

        // 同一 requestId 和相同内容只返回原消息，不重复写入或重复通知。
        mockMvc.perform(post("/conversations/{id}/messages", conversationId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(messageBody(requestId, "你好，请描述物品特征")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(messageId));

        mockMvc.perform(get("/notifications/unread-count")
                        .header("Authorization", applicant.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));

        // 复用 requestId 修改内容属于客户端错误，不能返回另一条消息。
        sendMessage(owner, conversationId, requestId, "不同的消息内容")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("该 requestId 已用于其他消息内容"));

        mockMvc.perform(get("/conversations/{id}/messages", conversationId)
                        .header("Authorization", applicant.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(messageId))
                .andExpect(jsonPath("$.data.records[0].sender.id").value(owner.userId()));

        mockMvc.perform(get("/notifications")
                        .queryParam("type", "CHAT_MESSAGE")
                        .header("Authorization", applicant.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].businessType").value("CONVERSATION"))
                .andExpect(jsonPath("$.data.records[0].businessId").value(conversationId));

        long chatNotificationId = objectMapper.readTree(
                        mockMvc.perform(get("/notifications")
                                        .queryParam("type", "CHAT_MESSAGE")
                                        .header("Authorization", applicant.authorization()))
                                .andReturn().getResponse().getContentAsString())
                .path("data").path("records").path(0).path("id").asLong();

        // 其他用户不能把别人的通知标记为已读。
        mockMvc.perform(patch("/notifications/{id}/read", chatNotificationId)
                        .header("Authorization", outsider.authorization()))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/notifications/{id}/read", chatNotificationId)
                        .header("Authorization", applicant.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));

        mockMvc.perform(get("/notifications/unread-count")
                        .header("Authorization", applicant.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    void createsNotificationsForDecisionsCompletionAndResolution() throws Exception {
        Session owner = registerAndLogin("notice_owner");
        Session applicant = registerAndLogin("notice_user");
        Session otherApplicant = registerAndLogin("notice_other");
        long postId = createPost(owner);
        long acceptedId = createApplication(applicant, postId);
        createApplication(otherApplicant, postId);

        decide(owner, acceptedId, "ACCEPT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        mockMvc.perform(get("/notifications")
                        .queryParam("type", "APPLICATION_ACCEPTED")
                        .header("Authorization", applicant.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/notifications")
                        .queryParam("type", "APPLICATION_REJECTED")
                        .header("Authorization", otherApplicant.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].title").value("申请未通过"));

        mockMvc.perform(patch("/applications/{id}/completion-request", acceptedId)
                        .header("Authorization", applicant.authorization()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/notifications")
                        .queryParam("type", "COMPLETION_REQUESTED")
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(patch("/posts/{id}/status", postId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"RESOLVED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/notifications")
                        .queryParam("type", "POST_RESOLVED")
                        .header("Authorization", applicant.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        // read-all 只更新当前用户仍未读的通知。
        mockMvc.perform(patch("/notifications/read-all")
                        .header("Authorization", applicant.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").isNumber());
        mockMvc.perform(patch("/notifications/read-all")
                        .header("Authorization", applicant.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(0));
    }

    private org.springframework.test.web.servlet.ResultActions sendMessage(
            Session sender, long conversationId, String requestId, String content)
            throws Exception {
        return mockMvc.perform(post("/conversations/{id}/messages", conversationId)
                .header("Authorization", sender.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(messageBody(requestId, content)));
    }

    private String messageBody(String requestId, String content) {
        return """
                {
                  "requestId": "%s",
                  "content": "%s"
                }
                """.formatted(requestId, content);
    }

    private String conversationBody(long postId, long peerUserId) {
        return """
                {
                  "postId": %d,
                  "peerUserId": %d
                }
                """.formatted(postId, peerUserId);
    }

    private long createApplication(Session applicant, long postId) throws Exception {
        String response = mockMvc.perform(post("/posts/{postId}/applications", postId)
                        .header("Authorization", applicant.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"我可以提供物品细节和拾取经过\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readId(response, "data");
    }

    private org.springframework.test.web.servlet.ResultActions decide(
            Session owner, long applicationId, String decision) throws Exception {
        return mockMvc.perform(patch("/applications/{id}/decision", applicationId)
                .header("Authorization", owner.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"" + decision + "\"}"));
    }

    private long createPost(Session owner) throws Exception {
        String response = mockMvc.perform(post("/posts")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postType": "FOUND",
                                  "title": "聊天联调校园卡",
                                  "description": "用于聊天和通知集成测试",
                                  "category": "证件卡片",
                                  "location": "图书馆",
                                  "occurrenceTime": "2026-10-23T10:30:00",
                                  "contactText": "值班室领取",
                                  "contactVisibility": "ACCEPTED_ONLY",
                                  "imageUrls": []
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readId(response, "data");
    }

    private Session registerAndLogin(String prefix) throws Exception {
        String safePrefix = prefix.substring(0, Math.min(prefix.length(), 8));
        String username = safePrefix + "_" + randomSuffix();
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
        JsonNode user = objectMapper.readTree(response).path("data");
        return new Session(
                "Bearer " + user.path("accessToken").asText(),
                user.path("user").path("id").asLong()
        );
    }

    private long readId(String response, String parent) {
        try {
            return objectMapper.readTree(response).path(parent).path("id").asLong();
        } catch (Exception exception) {
            throw new IllegalStateException("无法解析测试响应", exception);
        }
    }

    private String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private record Session(String authorization, long userId) {
    }
}

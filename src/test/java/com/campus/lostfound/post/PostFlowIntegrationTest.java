package com.campus.lostfound.post;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 帖子创建、筛选、编辑、关闭和权限边界的集成测试。
 *
 * <p>测试通过 MockMvc 调用真实接口，数据写入 H2 的 MySQL 兼容模式，
 * 每个测试使用随机用户名，避免共享内存数据库时互相冲突。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TokenRevocationService tokenRevocationService;

    @Test
    void createsFiltersUpdatesClosesAndProtectsOwnership() throws Exception {
        Session owner = registerAndLogin("owner");
        Session other = registerAndLogin("other");

        // 准备一条带图片和受限联系方式的 FOUND 帖子。
        String createBody = """
                {
                  "postType": "FOUND",
                  "title": "图书馆捡到校园卡",
                  "description": "卡面信息已做遮挡",
                  "category": "证件卡片",
                  "location": "图书馆一楼",
                  "occurrenceTime": "2026-10-23T10:30:00",
                  "contactText": "私信联系",
                  "contactVisibility": "ACCEPTED_ONLY",
                  "imageUrls": [
                    "https://example.com/card-front.jpg",
                    "https://example.com/card-back.jpg"
                  ]
                }
                """;

        String createdResponse = mockMvc.perform(post("/posts")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.imageUrls.length()").value(2))
                .andExpect(jsonPath("$.data.contactText").value("私信联系"))
                .andReturn().getResponse().getContentAsString();

        long postId = objectMapper.readTree(createdResponse).path("data").path("id").asLong();

        // 公开列表不返回完整描述，并支持类型和分类筛选。
        mockMvc.perform(get("/posts")
                        .queryParam("postType", "FOUND")
                        .queryParam("category", "证件卡片"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(postId))
                .andExpect(jsonPath("$.data.records[0].description").isEmpty());

        // 非发布者请求详情时，服务端应裁剪 ACCEPTED_ONLY 联系方式。
        mockMvc.perform(get("/posts/{id}", postId)
                        .header("Authorization", other.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactText").doesNotExist());

        String updateBody = """
                {
                  "postType": "FOUND",
                  "title": "图书馆捡到一张校园卡",
                  "description": "卡面信息已做遮挡",
                  "category": "证件卡片",
                  "location": "图书馆一楼",
                  "occurrenceTime": "2026-10-23T10:30:00",
                  "contactText": "私信联系",
                  "contactVisibility": "ACCEPTED_ONLY",
                  "imageUrls": ["https://example.com/card-new.jpg"]
                }
                """;

        // 所有权校验必须发生在写入之前。
        mockMvc.perform(put("/posts/{id}", postId)
                        .header("Authorization", other.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/posts/{id}", postId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("图书馆捡到一张校园卡"))
                .andExpect(jsonPath("$.data.imageUrls.length()").value(1));

        // 关闭是状态变更而不是物理删除，历史数据仍保留。
        mockMvc.perform(patch("/posts/{id}/status", postId)
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"CLOSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        // CLOSED 帖子对匿名用户不可见。
        mockMvc.perform(get("/posts/{id}", postId))
                .andExpect(status().isNotFound());

        // 发布者仍能从“我的帖子”看到 CLOSED 状态。
        mockMvc.perform(get("/users/me/posts")
                        .header("Authorization", owner.authorization()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value("CLOSED"));
    }

    @Test
    void rejectsInvalidCategory() throws Exception {
        Session owner = registerAndLogin("validation");
        String body = """
                {
                  "postType": "LOST",
                  "title": "丢失一本书",
                  "description": null,
                  "category": "不存在分类",
                  "location": "教学楼",
                  "occurrenceTime": "2026-10-23T10:30:00",
                  "contactText": null,
                  "contactVisibility": "OWNER_ONLY",
                  "imageUrls": ["https://example.com/1.jpg"]
                }
                """;

        mockMvc.perform(post("/posts")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("物品分类不合法"));
    }

    @Test
    void rejectsMoreThanSixImages() throws Exception {
        Session owner = registerAndLogin("images");
        String body = """
                {
                  "postType": "LOST",
                  "title": "丢失一本书",
                  "category": "书籍资料",
                  "location": "教学楼",
                  "occurrenceTime": "2026-10-23T10:30:00",
                  "contactVisibility": "OWNER_ONLY",
                  "imageUrls": [
                    "https://example.com/1.jpg",
                    "https://example.com/2.jpg",
                    "https://example.com/3.jpg",
                    "https://example.com/4.jpg",
                    "https://example.com/5.jpg",
                    "https://example.com/6.jpg",
                    "https://example.com/7.jpg"
                  ]
                }
                """;

        mockMvc.perform(post("/posts")
                        .header("Authorization", owner.authorization())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private Session registerAndLogin(String prefix) throws Exception {
        String safePrefix = prefix.substring(0, Math.min(prefix.length(), 8));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String username = safePrefix + "_" + suffix;
        String password = "Campus2026";
        String registerBody = """
                {
                  "username": "%s",
                  "password": "%s",
                  "nickname": "%s"
                }
                """.formatted(username, password, prefix);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());

        String loginBody = """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);
        String loginResponse = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(loginResponse);
        return new Session("Bearer " + json.path("data").path("accessToken").asText());
    }

    private record Session(String authorization) {
    }
}


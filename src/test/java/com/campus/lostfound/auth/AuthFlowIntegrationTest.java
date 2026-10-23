package com.campus.lostfound.auth;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TokenRevocationService tokenRevocationService;

    @Test
    void registerLoginReadProfileAndLogout() throws Exception {
        String username = "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String password = "Campus2026";

        String registerBody = """
                {
                  "username": "%s",
                  "password": "%s",
                  "nickname": "测试用户",
                  "phone": null,
                  "email": null
                }
                """.formatted(username, password);

        String registerResponse = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        JsonNode registered = objectMapper.readTree(registerResponse);
        assertThat(registered.path("traceId").asText()).isNotBlank();

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
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value(username))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(loginResponse)
                .path("data").path("accessToken").asText();

        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username));

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    void rejectsProtectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}


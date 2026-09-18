package com.enterpriseapp.procureflow.auth;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterpriseapp.procureflow.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class AuthControllerIT extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void registerThenLoginReturnsAJwtForANewUser() throws Exception {
    String email = "new.hire+" + System.nanoTime() + "@procureflow.test";
    String registerBody =
        objectMapper.writeValueAsString(new RegisterPayload(email, "Password123!", "New", "Hire"));

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.accessToken", notNullValue()))
        .andExpect(jsonPath("$.refreshToken", notNullValue()))
        .andExpect(jsonPath("$.user.roles[0]").value("ROLE_EMPLOYEE"));

    String loginBody = objectMapper.writeValueAsString(new LoginPayload(email, "Password123!"));
    mockMvc
        .perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken", notNullValue()))
        .andExpect(jsonPath("$.refreshToken", notNullValue()));
  }

  @Test
  void refreshTokenRotatesAndOldTokenCannotBeReused() throws Exception {
    String email = "refresh+" + System.nanoTime() + "@procureflow.test";
    String registerBody =
        objectMapper.writeValueAsString(new RegisterPayload(email, "Password123!", "Ref", "Resh"));
    MvcResult registerResult =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerBody))
            .andExpect(status().isCreated())
            .andReturn();
    String firstRefreshToken = readTree(registerResult).get("refreshToken").asText();

    String refreshBody = objectMapper.writeValueAsString(new RefreshPayload(firstRefreshToken));
    MvcResult refreshResult =
        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(refreshBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken", notNullValue()))
            .andReturn();
    String secondRefreshToken = readTree(refreshResult).get("refreshToken").asText();
    org.assertj.core.api.Assertions.assertThat(secondRefreshToken).isNotEqualTo(firstRefreshToken);

    // The rotated-away token is single-use: presenting it again is rejected.
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody))
        .andExpect(status().isUnauthorized());

    // The new token from rotation still works.
    String secondRefreshBody =
        objectMapper.writeValueAsString(new RefreshPayload(secondRefreshToken));
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(secondRefreshBody))
        .andExpect(status().isOk());
  }

  @Test
  void logoutRevokesTheRefreshTokenSoItCanNoLongerBeUsed() throws Exception {
    String email = "logout+" + System.nanoTime() + "@procureflow.test";
    String registerBody =
        objectMapper.writeValueAsString(new RegisterPayload(email, "Password123!", "Out", "User"));
    MvcResult registerResult =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerBody))
            .andExpect(status().isCreated())
            .andReturn();
    String refreshToken = readTree(registerResult).get("refreshToken").asText();
    String refreshBody = objectMapper.writeValueAsString(new RefreshPayload(refreshToken));

    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void refreshWithAnUnknownTokenIsRejected() throws Exception {
    String refreshBody =
        objectMapper.writeValueAsString(new RefreshPayload("not-a-real-refresh-token"));

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody))
        .andExpect(status().isUnauthorized());
  }

  private JsonNode readTree(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  @Test
  void loginWithWrongPasswordIsRejected() throws Exception {
    String loginBody =
        objectMapper.writeValueAsString(
            new LoginPayload("admin@procureflow.test", "wrong-password"));

    mockMvc
        .perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void registeringTheSameEmailTwiceIsRejected() throws Exception {
    String email = "duplicate+" + System.nanoTime() + "@procureflow.test";
    String body =
        objectMapper.writeValueAsString(new RegisterPayload(email, "Password123!", "Dup", "User"));

    mockMvc
        .perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict());
  }

  private record RegisterPayload(
      String email, String password, String firstName, String lastName) {}

  private record LoginPayload(String email, String password) {}

  private record RefreshPayload(String refreshToken) {}
}

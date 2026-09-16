package com.enterpriseapp.procureflow.auth;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterpriseapp.procureflow.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
        .andExpect(jsonPath("$.user.roles[0]").value("ROLE_EMPLOYEE"));

    String loginBody = objectMapper.writeValueAsString(new LoginPayload(email, "Password123!"));
    mockMvc
        .perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken", notNullValue()));
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
}

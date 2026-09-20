package com.enterpriseapp.procureflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterpriseapp.procureflow.security.JwtService;
import com.enterpriseapp.procureflow.user.User;
import com.enterpriseapp.procureflow.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The regression proof required by this feature: a JWT-registered user and an OIDC login end up as
 * the SAME local account (ADR-0005's identity-linking decision), and doing that linking never
 * breaks that account's existing password login. This exercises the exact claims a real Keycloak
 * container issues (not a hand-built {@code OidcUser}) through {@link UserService#
 * findOrProvisionForOidc}, then confirms both an OIDC-minted token and the original password both
 * authenticate against the real, running application.
 */
class OidcLoginProvisioningIT extends AbstractOidcIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserService userService;
  @Autowired private JwtService jwtService;

  @Test
  void oidcLoginProvisionsANewUserFromRealKeycloakClaims() throws Exception {
    String idToken = fetchRealIdToken(TEST_USER_NEW_EMAIL);
    Map<String, Object> claims = decodeClaimsWithoutVerifying(idToken);
    assertThat(claims.get("email")).isEqualTo(TEST_USER_NEW_EMAIL);
    assertThat(claims.get("sub")).isNotNull();

    User provisioned =
        userService.findOrProvisionForOidc(
            "keycloak",
            (String) claims.get("sub"),
            (String) claims.get("email"),
            (String) claims.get("given_name"),
            (String) claims.get("family_name"));

    assertThat(provisioned.getEmail()).isEqualTo(TEST_USER_NEW_EMAIL);
    assertThat(provisioned.getOidcProvider()).isEqualTo("keycloak");
    assertThat(provisioned.getOidcSubject()).isEqualTo(claims.get("sub"));

    // The app JWT minted for this OIDC-provisioned user must work exactly like any other.
    String appToken = mintAppJwtFor(provisioned);
    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", "Bearer " + appToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(TEST_USER_NEW_EMAIL))
        .andExpect(jsonPath("$.roles[0]").value("ROLE_EMPLOYEE"));
  }

  @Test
  void oidcLoginLinksAnExistingJwtAccountWithoutBreakingItsPasswordLogin() throws Exception {
    // Sanity baseline: the seeded JWT account logs in with its original password beforehand.
    assertPasswordLoginSucceeds();

    String idToken = fetchRealIdToken(TEST_USER_EXISTING_EMAIL);
    Map<String, Object> claims = decodeClaimsWithoutVerifying(idToken);

    User linked =
        userService.findOrProvisionForOidc(
            "keycloak",
            (String) claims.get("sub"),
            (String) claims.get("email"),
            (String) claims.get("given_name"),
            (String) claims.get("family_name"));

    assertThat(linked.getEmail()).isEqualTo(TEST_USER_EXISTING_EMAIL);
    assertThat(linked.getOidcSubject()).isEqualTo(claims.get("sub"));

    // Linking must not have disturbed the account's password - both paths work independently,
    // simultaneously, for the same underlying user.
    assertPasswordLoginSucceeds();

    String ssoMintedToken = mintAppJwtFor(linked);
    mockMvc
        .perform(get("/api/v1/users/me").header("Authorization", "Bearer " + ssoMintedToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(TEST_USER_EXISTING_EMAIL))
        .andExpect(jsonPath("$.id").value(linked.getId()));
  }

  private void assertPasswordLoginSucceeds() throws Exception {
    String loginBody =
        objectMapper.writeValueAsString(new LoginPayload(TEST_USER_EXISTING_EMAIL, "Password123!"));
    mockMvc
        .perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty());
  }

  private String mintAppJwtFor(User user) {
    Set<String> roles = user.getRoles().stream().map(Enum::name).collect(Collectors.toSet());
    return jwtService.generateAccessToken(user.getId(), user.getEmail(), roles);
  }

  private record LoginPayload(String email, String password) {}
}

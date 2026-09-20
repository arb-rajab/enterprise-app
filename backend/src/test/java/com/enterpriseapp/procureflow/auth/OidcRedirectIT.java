package com.enterpriseapp.procureflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves the {@code oauth2Login} wiring (registration + the dedicated {@code oidcFilterChain} in
 * {@code SecurityConfig}) sends the browser to the *real* Keycloak container's authorization
 * endpoint - not a stub. It deliberately stops there: following the redirect through Keycloak's
 * login form is exactly what Spring Security's own (well-tested) {@code oauth2Login} machinery
 * does; what's novel and worth proving end-to-end is the identity linking in {@link
 * OidcLoginProvisioningIT}.
 */
class OidcRedirectIT extends AbstractOidcIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void authorizationEndpointRedirectsToTheRealKeycloakContainer() throws Exception {
    String expectedAuthServerOrigin = KEYCLOAK.getAuthServerUrl();

    String location =
        mockMvc
            .perform(get("/oauth2/authorization/keycloak"))
            .andExpect(status().is3xxRedirection())
            .andReturn()
            .getResponse()
            .getRedirectedUrl();

    assertThat(location).isNotNull();
    assertThat(location).startsWith(expectedAuthServerOrigin);
    assertThat(location).contains("client_id=procureflow-backend");
    assertThat(location).contains("response_type=code");
  }

  @Test
  void apiAuthPathIsUnaffectedByTheOidcChainExisting() throws Exception {
    // The stateless JWT API chain (apiFilterChain, @Order(2)) still owns everything that isn't
    // /oauth2/** or /login/** - a regression check that adding the OIDC chain didn't silently
    // start intercepting API auth requests and, say, redirect them into the OIDC login flow
    // instead of the JWT API rejecting them outright.
    mockMvc.perform(get("/api/v1/users/me")).andExpect(status().is4xxClientError());
  }
}

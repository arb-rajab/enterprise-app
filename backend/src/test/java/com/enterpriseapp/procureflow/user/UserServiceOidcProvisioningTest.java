package com.enterpriseapp.procureflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit coverage for the identity-linking decision in
 * docs/project-memory/adr/0005-oidc-sso-identity-linking.md: an OIDC login links onto an existing
 * password-based account with the same email rather than creating a second, disconnected identity,
 * and never touches that account's existing password hash while doing so - the JWT login path for
 * the same user must be completely unaffected. The full round trip against a real Keycloak
 * container lives in {@code OidcLoginProvisioningIT}; this class isolates just the linking/creation
 * decision so it runs in `mvn test` with no Docker involved.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceOidcProvisioningTest {

  @Mock private UserRepository userRepository;
  @Mock private com.enterpriseapp.procureflow.department.DepartmentService departmentService;
  @Mock private PasswordEncoder passwordEncoder;

  private UserService userService;

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    userService = new UserService(userRepository, departmentService, passwordEncoder);
  }

  @Test
  void provisionsANewUserWhenNoAccountHasThatEmail() {
    when(userRepository.findByEmailIgnoreCase("sso.newhire@procureflow.test"))
        .thenReturn(Optional.empty());
    when(passwordEncoder.encode(any())).thenReturn("$2a$10$unguessable-random-hash");
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    User user =
        userService.findOrProvisionForOidc(
            "keycloak", "kc-subject-123", "sso.newhire@procureflow.test", "Sasha", "Newhire");

    assertThat(user.getEmail()).isEqualTo("sso.newhire@procureflow.test");
    assertThat(user.getOidcProvider()).isEqualTo("keycloak");
    assertThat(user.getOidcSubject()).isEqualTo("kc-subject-123");
    assertThat(user.getRoles()).containsExactly(RoleName.ROLE_EMPLOYEE);
    assertThat(user.isActive()).isTrue();
    assertThat(user.getPasswordHash()).isEqualTo("$2a$10$unguessable-random-hash");
  }

  @Test
  void linksAnExistingJwtRegisteredUserByEmailWithoutTouchingItsPassword() {
    User existing =
        User.builder()
            .email("manager@procureflow.test")
            .passwordHash("$2a$10$original-bcrypt-hash-from-registration")
            .firstName("Morgan")
            .lastName("Manager")
            .active(true)
            .roles(EnumSet.of(RoleName.ROLE_DEPARTMENT_MANAGER))
            .build();
    when(userRepository.findByEmailIgnoreCase("manager@procureflow.test"))
        .thenReturn(Optional.of(existing));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    User linked =
        userService.findOrProvisionForOidc(
            "keycloak", "kc-subject-456", "manager@procureflow.test", "Morgan", "Manager");

    assertThat(linked).isSameAs(existing);
    assertThat(linked.getOidcProvider()).isEqualTo("keycloak");
    assertThat(linked.getOidcSubject()).isEqualTo("kc-subject-456");
    // The whole point: linking must not touch the password JWT login still relies on.
    assertThat(linked.getPasswordHash()).isEqualTo("$2a$10$original-bcrypt-hash-from-registration");
    assertThat(linked.getRoles()).containsExactly(RoleName.ROLE_DEPARTMENT_MANAGER);
    verify(passwordEncoder, never()).encode(any());
  }
}

package com.enterpriseapp.procureflow.user;

import com.enterpriseapp.procureflow.common.exception.DuplicateResourceException;
import com.enterpriseapp.procureflow.common.exception.ResourceNotFoundException;
import com.enterpriseapp.procureflow.department.Department;
import com.enterpriseapp.procureflow.department.DepartmentService;
import com.enterpriseapp.procureflow.user.dto.RegisterRequest;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

  private final UserRepository userRepository;
  private final DepartmentService departmentService;
  private final PasswordEncoder passwordEncoder;

  @Transactional
  public User register(RegisterRequest request) {
    if (userRepository.existsByEmailIgnoreCase(request.email())) {
      throw new DuplicateResourceException(
          "An account with email " + request.email() + " already exists");
    }
    Department department =
        request.departmentId() != null ? departmentService.findById(request.departmentId()) : null;
    User user =
        User.builder()
            .email(request.email().toLowerCase())
            .passwordHash(passwordEncoder.encode(request.password()))
            .firstName(request.firstName())
            .lastName(request.lastName())
            .department(department)
            .active(true)
            .roles(EnumSet.of(RoleName.ROLE_EMPLOYEE))
            .build();
    return userRepository.save(user);
  }

  public User findById(Long id) {
    return userRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("User", id));
  }

  public User findByEmail(String email) {
    return userRepository
        .findByEmailIgnoreCase(email)
        .orElseThrow(() -> new ResourceNotFoundException("No user found with email " + email));
  }

  /**
   * Finds or creates the local user for an OIDC login, linking by email.
   *
   * <p>See {@code docs/project-memory/adr/0005-oidc-sso-identity-linking.md} for why this links
   * onto an existing password-based account with the same email rather than treating OIDC and JWT
   * logins as separate identities. A brand-new account is provisioned with a random, unguessable
   * BCrypt hash (never handed to the caller) so the JWT login path needs no null-password special
   * case for OIDC-only users - it just reports "bad credentials," as it would for any unknown
   * password.
   */
  @Transactional
  public User findOrProvisionForOidc(
      String provider, String subject, String email, String firstName, String lastName) {
    User user =
        userRepository
            .findByEmailIgnoreCase(email)
            .orElseGet(
                () ->
                    User.builder()
                        .email(email.toLowerCase())
                        .passwordHash(
                            passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                        .firstName(firstName)
                        .lastName(lastName)
                        .active(true)
                        .roles(EnumSet.of(RoleName.ROLE_EMPLOYEE))
                        .build());
    user.setOidcProvider(provider);
    user.setOidcSubject(subject);
    return userRepository.save(user);
  }

  public List<User> findAll() {
    return userRepository.findAll();
  }

  @Transactional
  public User replaceRoles(Long id, Set<RoleName> roles) {
    User user = findById(id);
    user.setRoles(EnumSet.copyOf(roles));
    return user;
  }

  @Transactional
  public User setActive(Long id, boolean active) {
    User user = findById(id);
    user.setActive(active);
    return user;
  }
}

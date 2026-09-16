package com.enterpriseapp.procureflow.user;

import com.enterpriseapp.procureflow.user.dto.UpdateRolesRequest;
import com.enterpriseapp.procureflow.user.dto.UserSummary;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @GetMapping("/me")
  public UserSummary me(Authentication authentication) {
    return UserSummary.from(userService.findByEmail(authentication.getName()));
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','PROCUREMENT_OFFICER')")
  public List<UserSummary> findAll() {
    return userService.findAll().stream().map(UserSummary::from).toList();
  }

  @PutMapping("/{id}/roles")
  @PreAuthorize("hasRole('ADMIN')")
  public UserSummary updateRoles(
      @PathVariable Long id, @Valid @RequestBody UpdateRolesRequest request) {
    return UserSummary.from(userService.replaceRoles(id, request.roles()));
  }
}

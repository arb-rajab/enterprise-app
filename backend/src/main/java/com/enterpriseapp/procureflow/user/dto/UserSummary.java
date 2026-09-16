package com.enterpriseapp.procureflow.user.dto;

import com.enterpriseapp.procureflow.user.User;
import java.util.Set;

public record UserSummary(
    Long id,
    String email,
    String firstName,
    String lastName,
    Long departmentId,
    Set<String> roles) {

  public static UserSummary from(User user) {
    return new UserSummary(
        user.getId(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        user.getDepartment() != null ? user.getDepartment().getId() : null,
        user.getRoles().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()));
  }
}

package com.enterpriseapp.procureflow.user.dto;

public record AuthResponse(
    String accessToken, String tokenType, long expiresInSeconds, UserSummary user) {

  public static AuthResponse bearer(String token, long expiresInSeconds, UserSummary user) {
    return new AuthResponse(token, "Bearer", expiresInSeconds, user);
  }
}

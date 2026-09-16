package com.enterpriseapp.procureflow.user;

import com.enterpriseapp.procureflow.security.JwtService;
import com.enterpriseapp.procureflow.user.dto.AuthResponse;
import com.enterpriseapp.procureflow.user.dto.LoginRequest;
import com.enterpriseapp.procureflow.user.dto.RegisterRequest;
import com.enterpriseapp.procureflow.user.dto.UserSummary;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final AuthenticationManager authenticationManager;
  private final UserService userService;
  private final JwtService jwtService;

  public AuthResponse register(RegisterRequest request) {
    User user = userService.register(request);
    return issueTokenFor(user);
  }

  public AuthResponse login(LoginRequest request) {
    authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(request.email(), request.password()));
    User user = userService.findByEmail(request.email());
    return issueTokenFor(user);
  }

  private AuthResponse issueTokenFor(User user) {
    Set<String> roles = user.getRoles().stream().map(Enum::name).collect(Collectors.toSet());
    String token = jwtService.generateAccessToken(user.getId(), user.getEmail(), roles);
    return AuthResponse.bearer(token, jwtService.accessTokenTtlSeconds(), UserSummary.from(user));
  }
}

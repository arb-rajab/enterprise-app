package com.enterpriseapp.procureflow.security;

import com.enterpriseapp.procureflow.user.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Adapts our {@link User} entity to Spring Security's authentication model. */
public class UserPrincipal implements UserDetails {

  private final User user;

  public UserPrincipal(User user) {
    this.user = user;
  }

  public Long getUserId() {
    return user.getId();
  }

  public User getUser() {
    return user;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return user.getRoles().stream().map(role -> new SimpleGrantedAuthority(role.name())).toList();
  }

  @Override
  public String getPassword() {
    return user.getPasswordHash();
  }

  @Override
  public String getUsername() {
    return user.getEmail();
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return user.isActive();
  }

  public static List<GrantedAuthority> authoritiesOf(User user) {
    return user.getRoles().stream()
        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role.name()))
        .toList();
  }
}

package com.akiramenai.videobackend.utility;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import com.akiramenai.videobackend.model.PolymorphicCredentials;

public class CustomAuthToken extends AbstractAuthenticationToken {
  private final String userEmail;
  private final String password;

  private final String jwtToken;

  public CustomAuthToken(String userEmail, String password, String jwtToken) {
    super(null);

    this.userEmail = userEmail;
    this.password = password;
    this.jwtToken = jwtToken;
  }

  public CustomAuthToken(String userEmail, String password, String jwtToken, boolean isTokenAuthenticated) {
    super(null);

    this.userEmail = userEmail;
    this.password = password;
    this.jwtToken = jwtToken;

    super.setAuthenticated(isTokenAuthenticated);
  }

  @Override
  public Object getCredentials() {
    return new PolymorphicCredentials(this.userEmail, this.password, this.jwtToken);
  }

  @Override
  public Object getPrincipal() {
    // This could be the user ID, username, or a UserDetails object after authentication
    return null; // Set after successful authentication
  }
}

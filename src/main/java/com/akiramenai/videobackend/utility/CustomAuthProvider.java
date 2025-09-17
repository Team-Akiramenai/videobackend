package com.akiramenai.videobackend.utility;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import com.akiramenai.videobackend.model.PolymorphicCredentials;
import com.akiramenai.videobackend.service.JWTService;

import java.util.Optional;

@Slf4j
@Component
public class CustomAuthProvider implements AuthenticationProvider {
  private final JWTService jwtService = new JWTService();

  @Override
  public Authentication authenticate(final Authentication authentication) throws AuthenticationException {
    CustomAuthToken token = (CustomAuthToken) authentication;
    PolymorphicCredentials polyCreds = (PolymorphicCredentials) token.getCredentials();

    if (polyCreds.userEmail() != null && polyCreds.password() != null) {
      // This type of authentication isn't available in this microservice
      log.warn("Expected JWT token based authentication, but got request to authenticate using E-mail and password instead.");
    }

    if (polyCreds.jwtToken() != null) {
      Optional<String> failureReason = jwtService.isTokenExpired(polyCreds.jwtToken());
      if (failureReason.isPresent()) {
        throw new BadCredentialsException("Authentication failed. Reason: " + failureReason.get());
      }

      return new CustomAuthToken(null, null, polyCreds.jwtToken());
    }

    throw new BadCredentialsException("Authentication failed. Reason: Both user email and JWT token are null.");
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return authentication.equals(CustomAuthToken.class);
  }
}
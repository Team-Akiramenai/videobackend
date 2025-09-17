package com.akiramenai.videobackend.filters;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.akiramenai.videobackend.model.JwtErrorTypes;
import com.akiramenai.videobackend.model.ResultOrError;
import com.akiramenai.videobackend.service.JWTService;
import com.akiramenai.videobackend.utility.CustomAuthToken;

import java.io.IOException;
import java.util.Optional;

@Component
public class JwtFilter extends OncePerRequestFilter {
  private final JWTService jwtService;

  public JwtFilter(JWTService jwtService) {
    this.jwtService = jwtService;
  }

  private void sendAuthFailedResponse(HttpServletResponse response, String reason) {
    response.setContentType("text/plain");
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    try {
      response.getWriter().println(reason);
      response.getWriter().flush();
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain
  ) throws ServletException, IOException {
    String authHeader = request.getHeader("Authorization");

    Optional<String> extractedJwtToken = JWTService.extractTokenFromAuthHeader(authHeader);
    if (extractedJwtToken.isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }

    String token = extractedJwtToken.get();

    ResultOrError<Claims, JwtErrorTypes> extracted = jwtService.extractClaim(token);
    if ((extracted.errorType() != null)) {
      sendAuthFailedResponse(response, "Authentication failed. Reason: " + extracted.errorMessage());
      return;
    }

    Claims claimsInToken = extracted.result();
    request.setAttribute("userId", claimsInToken.get("userId", String.class));
    request.setAttribute("userEmail", claimsInToken.getSubject());
    request.setAttribute("accountType", claimsInToken.get("accountType", String.class));

    if (SecurityContextHolder.getContext().getAuthentication() == null) {
      Object newAccessToken = request.getAttribute("newAccessToken");
      if (newAccessToken != null) {
        logger.info("Refreshed the expired access token.");
        token = newAccessToken.toString();
      }

      Optional<String> failureReason = jwtService.isTokenExpired(token);
      failureReason.ifPresent(reason -> sendAuthFailedResponse(response, "Authentication failed. Reason: " + reason));

      CustomAuthToken customAuthToken = new CustomAuthToken(null, null, token, true);

      SecurityContextHolder.getContext().setAuthentication(customAuthToken);
    }

    filterChain.doFilter(request, response);
  }
}

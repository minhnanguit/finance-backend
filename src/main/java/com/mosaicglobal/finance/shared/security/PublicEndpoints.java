package com.mosaicglobal.finance.shared.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * Single list of endpoints reachable without a token. Everything else needs a bearer JWT.
 *
 * <p>No {@code /api/v1/auth/**} entries any more: logging in happens at Keycloak, not here
 * (ADR-004).
 */
final class PublicEndpoints {

  private PublicEndpoints() {}

  static void permit(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
          registry) {
    registry
        .requestMatchers(
            HttpMethod.GET,
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/openapi.yaml",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**")
        .permitAll();
  }
}

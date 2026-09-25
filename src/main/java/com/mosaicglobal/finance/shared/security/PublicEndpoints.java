package com.mosaicglobal.finance.shared.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/** Danh sách duy nhất các endpoint không cần token. */
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

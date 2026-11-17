package com.uit.finance.shared.security;

import com.uit.finance.shared.web.idempotency.IdempotencyFilter;
import com.uit.finance.shared.web.limit.BodySizeLimitFilter;
import com.uit.finance.shared.web.limit.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Resource server thuần: Keycloak issue token, app này chỉ verify (ADR-004).
 *
 * <p>Issuer và JWKS cố ý cấu hình riêng: ở local, issuer trong token chỉ reachable từ emulator
 * ({@code 10.0.2.2}), còn process này fetch key qua {@code localhost}. Issuer vẫn được validate
 * chặt.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfiguration {

  @Bean
  JwtDecoder jwtDecoder(SecurityProperties security) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(security.jwkSetUri()).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<Jwt>(
            JwtValidators.createDefaultWithIssuer(security.issuerUri()),
            new AudienceValidator(security.audience())));
    return decoder;
  }

  @Bean
  SecurityFilterChain apiSecurityFilterChain(
      HttpSecurity http,
      ProblemAuthenticationEntryPoint problemHandler,
      RateLimitFilter rateLimitFilter,
      BodySizeLimitFilter bodySizeLimitFilter,
      IdempotencyFilter idempotencyFilter,
      CurrentUserResolver currentUserResolver)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            registry -> {
              PublicEndpoints.permit(registry);
              registry.anyRequest().authenticated();
            })
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(
                        jwt ->
                            jwt.jwtAuthenticationConverter(
                                new ProvisioningJwtAuthenticationConverter(currentUserResolver)))
                    .authenticationEntryPoint(problemHandler)
                    .accessDeniedHandler(problemHandler))
        .exceptionHandling(
            handling ->
                handling
                    .authenticationEntryPoint(problemHandler)
                    .accessDeniedHandler(problemHandler))
        // Sau authorization để đếm và scope theo user. Thứ tự: rate limit (rẻ nhất, chặn spam
        // trước) → giới hạn body (trước khi đọc body) → idempotency (đọc cả body vào bộ nhớ).
        .addFilterAfter(rateLimitFilter, AuthorizationFilter.class)
        .addFilterAfter(bodySizeLimitFilter, RateLimitFilter.class)
        .addFilterAfter(idempotencyFilter, BodySizeLimitFilter.class)
        .build();
  }
}

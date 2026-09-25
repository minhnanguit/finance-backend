package com.mosaicglobal.finance.shared.security;

import com.mosaicglobal.finance.shared.web.idempotency.IdempotencyFilter;
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
 * Stateless resource server. Tokens are minted by Keycloak (ADR-004); this application only
 * validates them and never issues one.
 *
 * <p>Issuer and JWKS URI are configured separately on purpose. In local development the issuer
 * baked into the token is reachable only from the Android emulator ({@code 10.0.2.2}), while this
 * process fetches keys over {@code localhost}. Validation still pins the issuer.
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
        // After authorization so the idempotency key can be scoped to the authenticated user.
        .addFilterAfter(idempotencyFilter, AuthorizationFilter.class)
        .build();
  }
}

package com.mosaicglobal.finance.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Resource-server settings. Declared here rather than reusing Spring Boot's own {@code
 * spring.security.oauth2.resourceserver.*} binding so the three values that matter are explicit,
 * validated together and unaffected by Boot's internal repackaging.
 *
 * @param issuerUri value the token's {@code iss} claim must equal. In local development this is the
 *     address the <em>emulator</em> reaches Keycloak on, which this process cannot resolve — hence
 *     the separate {@code jwkSetUri}.
 * @param jwkSetUri where <em>this process</em> fetches signing keys. Cached by the decoder.
 * @param audience value that must appear in {@code aud}. Without it, any token from the same realm
 *     — including one minted for another client — would be accepted.
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(String issuerUri, String jwkSetUri, String audience) {}

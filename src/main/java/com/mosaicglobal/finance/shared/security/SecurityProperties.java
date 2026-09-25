package com.mosaicglobal.finance.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Không dùng {@code spring.security.oauth2.resourceserver.*} của Boot vì class properties đó đã bị
 * dời package ở Boot 4; tự khai thì ba giá trị quan trọng nằm rõ một chỗ.
 *
 * @param issuerUri giá trị {@code iss} bắt buộc trong token. Ở local đây là địa chỉ
 *     <em>emulator</em> dùng để gọi Keycloak ({@code 10.0.2.2}), process này không gọi tới được —
 *     nên mới tách riêng {@code jwkSetUri}.
 * @param jwkSetUri nơi process này fetch signing key.
 * @param audience giá trị bắt buộc có trong {@code aud}.
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(String issuerUri, String jwkSetUri, String audience) {}

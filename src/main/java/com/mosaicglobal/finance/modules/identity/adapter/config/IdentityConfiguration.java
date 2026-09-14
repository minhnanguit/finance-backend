package com.mosaicglobal.finance.modules.identity.adapter.config;

import com.mosaicglobal.finance.modules.identity.application.service.SessionSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires configuration into the framework-free application layer. */
@Configuration(proxyBeanMethods = false)
class IdentityConfiguration {

  @Bean
  SessionSettings sessionSettings(IdentityProperties properties) {
    return new SessionSettings(properties.refreshTokenTtl());
  }
}

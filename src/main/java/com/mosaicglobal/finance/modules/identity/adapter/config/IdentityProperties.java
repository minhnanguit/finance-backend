package com.mosaicglobal.finance.modules.identity.adapter.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.identity")
record IdentityProperties(@DefaultValue("30d") Duration refreshTokenTtl) {}

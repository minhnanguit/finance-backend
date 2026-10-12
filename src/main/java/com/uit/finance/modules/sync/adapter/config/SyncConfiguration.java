package com.uit.finance.modules.sync.adapter.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Bật {@code @Scheduled} cho job dọn nhật ký op. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(SyncProperties.class)
class SyncConfiguration {}

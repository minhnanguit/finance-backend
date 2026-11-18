package com.uit.finance.modules.identity.adapter.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Bật {@code @Scheduled} cho job hoàn tất xoá tài khoản (không dựa vào module khác bật hộ). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class IdentityConfiguration {}

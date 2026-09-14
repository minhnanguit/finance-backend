package com.mosaicglobal.finance.shared.web.idempotency;

import com.mosaicglobal.finance.shared.web.ProblemDetailFactory;
import com.mosaicglobal.finance.shared.web.ProblemDetailWriter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
class IdempotencyConfiguration {

  @Bean
  IdempotencyStore idempotencyStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
    return new RedisIdempotencyStore(redis, objectMapper);
  }

  @Bean
  IdempotencyFilter idempotencyFilter(
      IdempotencyStore store,
      IdempotencyProperties properties,
      ProblemDetailFactory problems,
      ProblemDetailWriter writer) {
    return new IdempotencyFilter(store, properties, problems, writer);
  }

  /**
   * The filter must run inside the Spring Security chain (after authentication, so the key can be
   * scoped per user). Disable the automatic servlet registration to avoid running it twice.
   */
  @Bean
  FilterRegistrationBean<IdempotencyFilter> idempotencyFilterRegistration(
      IdempotencyFilter filter) {
    FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}

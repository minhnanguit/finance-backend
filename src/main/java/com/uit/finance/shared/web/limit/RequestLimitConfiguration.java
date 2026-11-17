package com.uit.finance.shared.web.limit;

import com.uit.finance.shared.web.ProblemDetailFactory;
import com.uit.finance.shared.web.ProblemDetailWriter;
import java.time.Clock;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Hai filter chạy trong chain của Spring Security (xem {@code SecurityConfiguration}): sau xác
 * thực, trước {@code IdempotencyFilter} (filter đó đọc cả body vào bộ nhớ). Tắt đăng ký servlet tự
 * động để không chạy hai lần.
 */
@Configuration(proxyBeanMethods = false)
class RequestLimitConfiguration {

  @Bean
  RateLimiter rateLimiter(StringRedisTemplate redis, Clock clock) {
    return new RedisRateLimiter(redis, clock);
  }

  @Bean
  RateLimitFilter rateLimitFilter(
      RequestLimitProperties properties,
      RateLimiter limiter,
      ProblemDetailFactory problems,
      ProblemDetailWriter writer) {
    return new RateLimitFilter(properties, limiter, problems, writer);
  }

  @Bean
  BodySizeLimitFilter bodySizeLimitFilter(
      RequestLimitProperties properties,
      ProblemDetailFactory problems,
      ProblemDetailWriter writer) {
    return new BodySizeLimitFilter(properties, problems, writer);
  }

  @Bean
  FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
    FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  FilterRegistrationBean<BodySizeLimitFilter> bodySizeLimitFilterRegistration(
      BodySizeLimitFilter filter) {
    FilterRegistrationBean<BodySizeLimitFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}

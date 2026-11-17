package com.uit.finance.shared.web.limit;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.shared.web.ProblemDetailFactory;
import com.uit.finance.shared.web.ProblemDetailWriter;
import com.uit.finance.shared.web.limit.RequestLimitProperties.BodyLimitRule;
import com.uit.finance.shared.web.limit.RequestLimitProperties.RateLimitRule;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.Filter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.json.JsonMapper;

class RequestLimitFiltersTest {

  private static final String PUSH = "/api/v1/sync/push";

  private final ProblemDetailFactory problems = new ProblemDetailFactory(noTracer());
  private final ProblemDetailWriter writer = new ProblemDetailWriter(JsonMapper.builder().build());
  private final RequestLimitProperties properties =
      new RequestLimitProperties(
          true,
          List.of(new RateLimitRule("sync-push", "POST", PUSH, 2, Duration.ofMinutes(1))),
          List.of(new BodyLimitRule("POST", PUSH, DataSize.ofBytes(10))));

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  private static void signIn(String user) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "n/a", AuthorityUtils.NO_AUTHORITIES));
  }

  private static MockHttpServletRequest post(String path, String body) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
    request.setContent(body.getBytes(StandardCharsets.UTF_8));
    return request;
  }

  /** Body chunked: không có Content-Length. */
  private static MockHttpServletRequest chunked(String body) {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", PUSH) {
          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };
    request.setContent(body.getBytes(StandardCharsets.UTF_8));
    return request;
  }

  private static MockHttpServletResponse run(Filter filter, MockHttpServletRequest request)
      throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }

  /** Bộ đếm trong bộ nhớ, mỗi bucket một số. */
  private static final class CountingLimiter implements RateLimiter {
    final java.util.Map<String, AtomicInteger> counts = new java.util.HashMap<>();

    @Override
    public Decision tryAcquire(String bucket, int limit, Duration window) {
      int count = counts.computeIfAbsent(bucket, b -> new AtomicInteger()).incrementAndGet();
      return count <= limit ? Decision.allow() : new Decision(false, 42);
    }
  }

  @Test
  @DisplayName("rate limit: tới limit thì cho qua, vượt thì 429 + Retry-After; đếm riêng từng user")
  void rateLimitPerUser() throws Exception {
    RateLimitFilter filter =
        new RateLimitFilter(properties, new CountingLimiter(), problems, writer);
    signIn("ann");
    run(filter, post(PUSH, "{}"));
    run(filter, post(PUSH, "{}"));

    MockHttpServletResponse third = run(filter, post(PUSH, "{}"));
    signIn("bob");
    MockHttpServletResponse bobsFirst = run(filter, post(PUSH, "{}"));

    assertThat(third.getStatus()).isEqualTo(429);
    assertThat(third.getHeader("Retry-After")).isEqualTo("42");
    assertThat(third.getContentAsString()).contains("request.rate_limited");
    assertThat(bobsFirst.getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("rate limit chỉ áp cho endpoint đã khai báo")
  void otherEndpointsAreNotLimited() throws Exception {
    CountingLimiter limiter = new CountingLimiter();
    RateLimitFilter filter = new RateLimitFilter(properties, limiter, problems, writer);
    signIn("ann");

    run(filter, post("/api/v1/other", "{}"));

    assertThat(limiter.counts).isEmpty();
  }

  @Test
  @DisplayName("body có Content-Length vượt giới hạn: 413, không đọc body")
  void declaredLengthOverLimit() throws Exception {
    BodySizeLimitFilter filter = new BodySizeLimitFilter(properties, problems, writer);

    MockHttpServletResponse tooBig = run(filter, post(PUSH, "x".repeat(11)));
    MockHttpServletResponse fits = run(filter, post(PUSH, "x".repeat(10)));

    assertThat(tooBig.getStatus()).isEqualTo(413);
    assertThat(tooBig.getContentAsString()).contains("request.too_large");
    assertThat(fits.getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("body chunked: đọc tối đa giới hạn + 1 byte, vượt thì 413")
  void chunkedBodyOverLimit() throws Exception {
    BodySizeLimitFilter filter = new BodySizeLimitFilter(properties, problems, writer);

    assertThat(run(filter, chunked("x".repeat(11))).getStatus()).isEqualTo(413);
    assertThat(run(filter, chunked("x".repeat(10))).getStatus()).isEqualTo(200);
  }

  private static ObjectProvider<Tracer> noTracer() {
    return new ObjectProvider<>() {
      @Override
      public Tracer getObject() {
        throw new UnsupportedOperationException();
      }

      @Override
      public Tracer getIfAvailable() {
        return null;
      }
    };
  }
}

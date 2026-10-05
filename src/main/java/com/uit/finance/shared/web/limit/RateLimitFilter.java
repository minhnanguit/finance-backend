package com.uit.finance.shared.web.limit;

import com.uit.finance.shared.web.ProblemDetailFactory;
import com.uit.finance.shared.web.ProblemDetailWriter;
import com.uit.finance.shared.web.limit.RequestLimitProperties.RateLimitRule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Giới hạn tần suất theo user cho các endpoint khai trong {@link RequestLimitProperties} (ADR-006
 * B4). Chạy trong chain của Spring Security, sau xác thực, để đếm theo user chứ không theo IP.
 *
 * <p>Vượt giới hạn → {@code 429} + {@code Retry-After}; app chờ đúng thời gian đó rồi gửi lại.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  static final String CODE = "request.rate_limited";

  private final RequestLimitProperties properties;
  private final RateLimiter limiter;
  private final ProblemDetailFactory problems;
  private final ProblemDetailWriter writer;

  public RateLimitFilter(
      RequestLimitProperties properties,
      RateLimiter limiter,
      ProblemDetailFactory problems,
      ProblemDetailWriter writer) {
    this.properties = properties;
    this.limiter = limiter;
    this.problems = problems;
    this.writer = writer;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !properties.rateLimitEnabled() || rule(request).isEmpty();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Optional<String> user = authenticatedUser();
    Optional<RateLimitRule> rule = rule(request);
    if (user.isEmpty() || rule.isEmpty()) {
      chain.doFilter(request, response);
      return;
    }

    RateLimiter.Decision decision =
        limiter.tryAcquire(
            rule.get().name() + ':' + user.get(), rule.get().limit(), rule.get().window());
    if (decision.allowed()) {
      chain.doFilter(request, response);
      return;
    }
    response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
    writer.write(
        response,
        problems.create(
            HttpStatus.TOO_MANY_REQUESTS,
            CODE,
            "Too many requests; retry after " + decision.retryAfterSeconds() + " seconds"));
  }

  private Optional<RateLimitRule> rule(HttpServletRequest request) {
    return properties.rateLimits().stream()
        .filter(candidate -> candidate.matches(request.getMethod(), request.getRequestURI()))
        .findFirst();
  }

  private static Optional<String> authenticatedUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    boolean authenticated =
        auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    return authenticated ? Optional.of(auth.getName()) : Optional.empty();
  }
}

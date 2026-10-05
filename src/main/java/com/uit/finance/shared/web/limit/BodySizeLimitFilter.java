package com.uit.finance.shared.web.limit;

import com.uit.finance.shared.web.CachedBodyHttpServletRequest;
import com.uit.finance.shared.web.ProblemDetailFactory;
import com.uit.finance.shared.web.ProblemDetailWriter;
import com.uit.finance.shared.web.limit.RequestLimitProperties.BodyLimitRule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Giới hạn kích thước body theo endpoint (ADR-006 B4).
 *
 * <ul>
 *   <li>Có {@code Content-Length}: chặn ngay, không đọc byte nào của body.
 *   <li>Không có (body dạng chunked): đọc tối đa giới hạn + 1 byte vào bộ nhớ, quá thì chặn. Không
 *       bao giờ giữ quá giới hạn trong bộ nhớ, dù client gửi bao nhiêu.
 * </ul>
 */
public class BodySizeLimitFilter extends OncePerRequestFilter {

  static final String TOO_LARGE = "request.too_large";

  private final RequestLimitProperties properties;
  private final ProblemDetailFactory problems;
  private final ProblemDetailWriter writer;

  public BodySizeLimitFilter(
      RequestLimitProperties properties,
      ProblemDetailFactory problems,
      ProblemDetailWriter writer) {
    this.properties = properties;
    this.problems = problems;
    this.writer = writer;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return rule(request).isEmpty();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    BodyLimitRule rule = rule(request).orElseThrow();
    int maxBytes = Math.toIntExact(rule.maxSize().toBytes());
    long declared = request.getContentLengthLong();
    if (declared > maxBytes) {
      reject(response, rule);
      return;
    }
    if (declared >= 0) {
      chain.doFilter(request, response);
      return;
    }
    try {
      chain.doFilter(new CachedBodyHttpServletRequest(request, maxBytes), response);
    } catch (CachedBodyHttpServletRequest.BodyTooLargeException e) {
      reject(response, rule);
    }
  }

  private void reject(HttpServletResponse response, BodyLimitRule rule) throws IOException {
    writer.write(
        response,
        problems.create(
            HttpStatus.PAYLOAD_TOO_LARGE,
            TOO_LARGE,
            "Request body exceeds " + rule.maxSize().toKilobytes() + " KB"));
  }

  private Optional<BodyLimitRule> rule(HttpServletRequest request) {
    return properties.bodyLimits().stream()
        .filter(candidate -> candidate.matches(request.getMethod(), request.getRequestURI()))
        .findFirst();
  }
}

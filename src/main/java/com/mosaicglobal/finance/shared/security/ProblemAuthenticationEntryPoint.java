package com.mosaicglobal.finance.shared.security;

import com.mosaicglobal.finance.shared.web.ProblemDetailFactory;
import com.mosaicglobal.finance.shared.web.ProblemDetailWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Trả 401 / 403 dạng RFC 7807 để đồng bộ với mọi error response khác của API. */
@Component
class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final ProblemDetailFactory problems;
  private final ProblemDetailWriter writer;

  ProblemAuthenticationEntryPoint(ProblemDetailFactory problems, ProblemDetailWriter writer) {
    this.problems = problems;
    this.writer = writer;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException {
    writer.write(
        response,
        problems.create(
            HttpStatus.UNAUTHORIZED, "auth.unauthenticated", "Authentication required"));
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
      throws IOException {
    writer.write(
        response, problems.create(HttpStatus.FORBIDDEN, "auth.forbidden", "Access denied"));
  }
}

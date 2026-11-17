package com.uit.finance.shared.web;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Builds RFC 7807 problems with the project's extensions: a stable {@code code} and the current
 * {@code traceId} so a client error can be correlated with server logs.
 */
@Component
public class ProblemDetailFactory {

  public static final String CODE = "code";
  public static final String TRACE_ID = "traceId";
  private static final String TYPE_PREFIX = "urn:finance:problem:";

  private final ObjectProvider<Tracer> tracer;

  public ProblemDetailFactory(ObjectProvider<Tracer> tracer) {
    this.tracer = tracer;
  }

  public ProblemDetail create(HttpStatusCode status, String code, @Nullable String detail) {
    ProblemDetail problem = ProblemDetail.forStatus(status);
    if (detail != null) {
      problem.setDetail(detail);
    }
    return enrich(problem, code);
  }

  /** Adds type, title, code and traceId to a problem created elsewhere (e.g. by Spring MVC). */
  public ProblemDetail enrich(ProblemDetail problem, String defaultCode) {
    Map<String, Object> props = problem.getProperties();
    String code =
        props != null && props.get(CODE) instanceof String existing ? existing : defaultCode;
    problem.setType(URI.create(TYPE_PREFIX + code));
    if (problem.getTitle() == null) {
      HttpStatus resolved = HttpStatus.resolve(problem.getStatus());
      problem.setTitle(resolved != null ? resolved.getReasonPhrase() : "Error");
    }
    problem.setProperty(CODE, code);
    currentTraceId().ifPresent(id -> problem.setProperty(TRACE_ID, id));
    return problem;
  }

  /** Flat JSON representation: standard members first, then extension members. */
  public static Map<String, Object> toMap(ProblemDetail problem) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", problem.getType().toString());
    body.put("title", problem.getTitle());
    body.put("status", problem.getStatus());
    if (problem.getDetail() != null) {
      body.put("detail", problem.getDetail());
    }
    if (problem.getInstance() != null) {
      body.put("instance", problem.getInstance().toString());
    }
    if (problem.getProperties() != null) {
      body.putAll(problem.getProperties());
    }
    return body;
  }

  private Optional<String> currentTraceId() {
    Tracer current = tracer.getIfAvailable();
    if (current == null) {
      return Optional.empty();
    }
    Span span = current.currentSpan();
    return span == null ? Optional.empty() : Optional.of(span.context().traceId());
  }
}

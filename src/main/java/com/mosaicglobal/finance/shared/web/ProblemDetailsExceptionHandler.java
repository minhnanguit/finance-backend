package com.mosaicglobal.finance.shared.web;

import com.mosaicglobal.finance.shared.kernel.DomainException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates every failure into an RFC 7807 problem.
 *
 * <ul>
 *   <li>{@link DomainException} → status from its {@code ErrorCategory}, {@code code} from the
 *       exception
 *   <li>Value-object guard failures ({@link IllegalArgumentException}) → 400 {@code
 *       request.invalid}
 *   <li>Bean-validation failures → 400 {@code request.validation_failed} with a field list
 *   <li>Anything unexpected → 500 without leaking internals
 * </ul>
 */
@RestControllerAdvice
public class ProblemDetailsExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ProblemDetailsExceptionHandler.class);

  private final ProblemDetailFactory problems;

  public ProblemDetailsExceptionHandler(ProblemDetailFactory problems) {
    this.problems = problems;
  }

  @ExceptionHandler(DomainException.class)
  ResponseEntity<Object> handleDomain(DomainException ex) {
    HttpStatus status = HttpStatusMapping.of(ex.category());
    if (status.is5xxServerError()) {
      log.error("Domain failure mapped to 5xx", ex);
    }
    return respond(problems.create(status, ex.code(), ex.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex) {
    return respond(problems.create(HttpStatus.BAD_REQUEST, "request.invalid", ex.getMessage()));
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<Object> handleAuthentication(AuthenticationException ex) {
    return respond(
        problems.create(
            HttpStatus.UNAUTHORIZED, "auth.unauthenticated", "Authentication required"));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex) {
    return respond(problems.create(HttpStatus.FORBIDDEN, "auth.forbidden", "Access denied"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    return respond(
        problems.create(
            HttpStatus.INTERNAL_SERVER_ERROR, "server.error", "An unexpected error occurred"));
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem =
        problems.create(
            HttpStatus.BAD_REQUEST, "request.validation_failed", "Request validation failed");
    List<Map<String, String>> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                fe ->
                    Map.of(
                        "field",
                        fe.getField(),
                        "message",
                        Objects.requireNonNullElse(fe.getDefaultMessage(), "invalid")))
            .toList();
    problem.setProperty("errors", errors);
    return respond(problem);
  }

  /**
   * Every other Spring MVC problem (unreadable body, wrong media type, ...) gets our extensions.
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    ProblemDetail problem =
        body instanceof ProblemDetail existing ? existing : ProblemDetail.forStatus(statusCode);
    return respond(problems.enrich(problem, "request.invalid"));
  }

  private static ResponseEntity<Object> respond(ProblemDetail problem) {
    return ResponseEntity.status(problem.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(ProblemDetailFactory.toMap(problem));
  }
}

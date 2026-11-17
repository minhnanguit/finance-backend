package com.uit.finance.shared.web.idempotency;

import com.uit.finance.shared.web.CachedBodyHttpServletRequest;
import com.uit.finance.shared.web.ProblemDetailFactory;
import com.uit.finance.shared.web.ProblemDetailWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Makes every POST/PUT/PATCH under the protected prefixes idempotent.
 *
 * <ol>
 *   <li>{@code Idempotency-Key} header is required and must be a UUID → otherwise 400
 *   <li>Same key + same body while the first call is still running → 409
 *   <li>Same key + different body → 422
 *   <li>Same key + same body after completion → the stored response is replayed with {@code
 *       Idempotency-Replayed: true}
 * </ol>
 *
 * <p>Keys are scoped per authenticated principal (or "anonymous"), method and path. Responses with
 * 5xx status are not stored so the client can retry.
 */
public class IdempotencyFilter extends OncePerRequestFilter {

  public static final String HEADER = "Idempotency-Key";
  public static final String REPLAYED_HEADER = "Idempotency-Replayed";
  private static final Set<String> PROTECTED_METHODS = Set.of("POST", "PUT", "PATCH");
  private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);

  private final IdempotencyStore store;
  private final IdempotencyProperties properties;
  private final ProblemDetailFactory problems;
  private final ProblemDetailWriter writer;

  public IdempotencyFilter(
      IdempotencyStore store,
      IdempotencyProperties properties,
      ProblemDetailFactory problems,
      ProblemDetailWriter writer) {
    this.store = store;
    this.properties = properties;
    this.problems = problems;
    this.writer = writer;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!PROTECTED_METHODS.contains(request.getMethod())) {
      return true;
    }
    String path = request.getRequestURI();
    return properties.protectedPathPrefixes().stream().noneMatch(path::startsWith);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    Optional<UUID> key = parseKey(request.getHeader(HEADER));
    if (key.isEmpty()) {
      reject(
          response,
          HttpStatus.BAD_REQUEST,
          "request.idempotency_key_invalid",
          HEADER + " header is required and must be a UUID");
      return;
    }

    CachedBodyHttpServletRequest cached;
    try {
      cached = new CachedBodyHttpServletRequest(request, properties.maxBodyBytes());
    } catch (CachedBodyHttpServletRequest.BodyTooLargeException e) {
      reject(response, HttpStatus.PAYLOAD_TOO_LARGE, "request.too_large", e.getMessage());
      return;
    }

    String fingerprint = Fingerprint.of(cached.body());
    String storeKey = storeKey(request, key.get());

    Optional<IdempotencyRecord> existing = store.find(storeKey);
    if (existing.isPresent()) {
      replayOrReject(existing.get(), fingerprint, response);
      return;
    }
    if (!store.tryLock(storeKey, fingerprint, properties.inProgressTtl())) {
      // Lost the race with a concurrent identical request.
      Optional<IdempotencyRecord> raced = store.find(storeKey);
      if (raced.isPresent()) {
        replayOrReject(raced.get(), fingerprint, response);
      } else {
        reject(
            response,
            HttpStatus.CONFLICT,
            "request.idempotency_in_progress",
            "Request in progress");
      }
      return;
    }

    ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
    boolean stored = false;
    try {
      chain.doFilter(cached, wrapped);
      int status = wrapped.getStatus();
      if (status < 500) {
        store.complete(
            storeKey,
            IdempotencyRecord.completed(
                fingerprint, status, wrapped.getContentType(), wrapped.getContentAsByteArray()),
            properties.ttl());
        stored = true;
      }
    } finally {
      if (!stored) {
        safeRelease(storeKey);
      }
      wrapped.copyBodyToResponse();
    }
  }

  private void replayOrReject(
      IdempotencyRecord record, String fingerprint, HttpServletResponse response)
      throws IOException {
    if (!record.fingerprint().equals(fingerprint)) {
      reject(
          response,
          HttpStatus.UNPROCESSABLE_CONTENT,
          "request.idempotency_key_reused",
          HEADER + " was already used with a different payload");
      return;
    }
    if (!record.isCompleted()) {
      reject(
          response,
          HttpStatus.CONFLICT,
          "request.idempotency_in_progress",
          "A request with this " + HEADER + " is still being processed");
      return;
    }
    response.setStatus(record.status() != null ? record.status() : HttpStatus.OK.value());
    if (record.contentType() != null) {
      response.setContentType(record.contentType());
    }
    response.setHeader(REPLAYED_HEADER, "true");
    if (record.body() != null) {
      response.getOutputStream().write(record.body());
    }
    response.flushBuffer();
  }

  private void reject(HttpServletResponse response, HttpStatus status, String code, String detail)
      throws IOException {
    writer.write(response, problems.create(status, code, detail));
  }

  private void safeRelease(String storeKey) {
    try {
      store.release(storeKey);
    } catch (RuntimeException e) {
      log.warn("Could not release idempotency lock {}", storeKey, e);
    }
  }

  private static Optional<UUID> parseKey(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(UUID.fromString(raw.trim()));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static String storeKey(HttpServletRequest request, UUID key) {
    return "idem:v1:"
        + principalName()
        + ':'
        + request.getMethod()
        + ':'
        + request.getRequestURI()
        + ':'
        + key;
  }

  private static String principalName() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    boolean authenticated =
        auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    return authenticated ? auth.getName() : "anonymous";
  }
}

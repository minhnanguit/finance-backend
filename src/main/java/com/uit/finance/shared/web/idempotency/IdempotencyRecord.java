package com.uit.finance.shared.web.idempotency;

import org.jspecify.annotations.Nullable;

/** What we remember about a request seen under an idempotency key. */
public record IdempotencyRecord(
    State state,
    String fingerprint,
    @Nullable Integer status,
    @Nullable String contentType,
    byte @Nullable [] body) {

  public enum State {
    IN_PROGRESS,
    COMPLETED
  }

  public static IdempotencyRecord inProgress(String fingerprint) {
    return new IdempotencyRecord(State.IN_PROGRESS, fingerprint, null, null, null);
  }

  public static IdempotencyRecord completed(
      String fingerprint, int status, @Nullable String contentType, byte[] body) {
    return new IdempotencyRecord(State.COMPLETED, fingerprint, status, contentType, body);
  }

  public boolean isCompleted() {
    return state == State.COMPLETED;
  }
}

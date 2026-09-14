package com.mosaicglobal.finance.shared.web.idempotency;

import java.time.Duration;
import java.util.Optional;

/** Storage abstraction for idempotency records; the filter never talks to Redis directly. */
public interface IdempotencyStore {

  Optional<IdempotencyRecord> find(String key);

  /** Atomically claims the key for the first request. Returns false if someone else holds it. */
  boolean tryLock(String key, String fingerprint, Duration ttl);

  void complete(String key, IdempotencyRecord record, Duration ttl);

  void release(String key);
}

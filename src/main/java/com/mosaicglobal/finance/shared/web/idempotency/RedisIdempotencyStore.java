package com.mosaicglobal.finance.shared.web.idempotency;

import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

/** Redis-backed store: one JSON value per key, TTL-managed by Redis. */
class RedisIdempotencyStore implements IdempotencyStore {

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  RedisIdempotencyStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
    this.redis = redis;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<IdempotencyRecord> find(String key) {
    String json = redis.opsForValue().get(key);
    return json == null
        ? Optional.empty()
        : Optional.of(objectMapper.readValue(json, IdempotencyRecord.class));
  }

  @Override
  public boolean tryLock(String key, String fingerprint, Duration ttl) {
    String json = objectMapper.writeValueAsString(IdempotencyRecord.inProgress(fingerprint));
    return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, json, ttl));
  }

  @Override
  public void complete(String key, IdempotencyRecord record, Duration ttl) {
    redis.opsForValue().set(key, objectMapper.writeValueAsString(record), ttl);
  }

  @Override
  public void release(String key) {
    redis.delete(key);
  }
}

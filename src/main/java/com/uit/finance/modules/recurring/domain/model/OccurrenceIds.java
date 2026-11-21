package com.uit.finance.modules.recurring.domain.model;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Id giao dịch của một kỳ: {@code UUIDv5(NAMESPACE, "<recurringId>/<yyyy-MM-dd>")} (ADR-009 §2, RFC
 * 4122 §4.3). App tính đúng công thức này; server giữ để test vector hai phía khớp nhau và để sau
 * này server có sinh cũng không trùng.
 */
public final class OccurrenceIds {

  /** Không bao giờ đổi: đổi là mọi máy sinh lại toàn bộ kỳ. */
  public static final UUID NAMESPACE = UUID.fromString("9ec1e9e3-0bb0-485a-ba55-77182d53d5f4");

  private OccurrenceIds() {}

  public static UUID of(RecurringId recurringId, LocalDate scheduledDate) {
    return uuidV5(NAMESPACE, recurringId.value() + "/" + scheduledDate);
  }

  /** UUID name-based SHA-1 (version 5, variant RFC 4122). */
  public static UUID uuidV5(UUID namespace, String name) {
    MessageDigest sha1;
    try {
      sha1 = MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-1 is required by every Java runtime", e);
    }
    ByteBuffer ns = ByteBuffer.allocate(16);
    ns.putLong(namespace.getMostSignificantBits());
    ns.putLong(namespace.getLeastSignificantBits());
    sha1.update(ns.array());
    byte[] hash = sha1.digest(name.getBytes(StandardCharsets.UTF_8));
    hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);
    hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);
    ByteBuffer out = ByteBuffer.wrap(hash, 0, 16);
    return new UUID(out.getLong(), out.getLong());
  }
}

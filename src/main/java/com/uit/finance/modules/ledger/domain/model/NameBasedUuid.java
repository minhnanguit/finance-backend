package com.uit.finance.modules.ledger.domain.model;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/** UUID version 5 (RFC 9562 §5.5): SHA-1 của namespace + name, cùng input luôn ra cùng id. */
final class NameBasedUuid {

  private NameBasedUuid() {}

  static UUID v5(UUID namespace, String name) {
    MessageDigest sha1;
    try {
      sha1 = MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Every JVM must provide SHA-1", e);
    }
    sha1.update(
        ByteBuffer.allocate(16)
            .putLong(namespace.getMostSignificantBits())
            .putLong(namespace.getLeastSignificantBits())
            .array());
    byte[] hash = sha1.digest(name.getBytes(StandardCharsets.UTF_8));
    hash[6] = (byte) ((hash[6] & 0x0f) | 0x50); // version 5
    hash[8] = (byte) ((hash[8] & 0x3f) | 0x80); // variant RFC 4122
    ByteBuffer bytes = ByteBuffer.wrap(hash, 0, 16);
    return new UUID(bytes.getLong(), bytes.getLong());
  }
}

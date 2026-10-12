package com.uit.finance.modules.ledger.application.service.shared;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Tạo bản ghi theo id do client sinh, an toàn khi client gửi lại và khi 2 request cùng id chạy song
 * song (ADR-002 §7).
 *
 * <ol>
 *   <li>Đã có bản của chính user: trả bản đó, không ghi, không chạy {@code create}.
 *   <li>Insert được: trả bản vừa tạo.
 *   <li>Insert thua vì id đã có: đọc lại theo user. Thấy là request song song của chính user, trả
 *       bản đó. Không thấy là id của người khác: ném {@code notOwned}, không ghi đè (ADR-006 B1).
 * </ol>
 *
 * <p>Không dùng exception để rẽ nhánh insert/đọc lại, vì exception đi qua proxy
 * {@code @Transactional} sẽ đánh dấu cả transaction là rollback-only.
 */
public final class CreateOnce {

  private CreateOnce() {}

  public static <T> T run(
      Supplier<Optional<T>> findOwned,
      Supplier<T> create,
      Predicate<T> insertIfAbsent,
      Supplier<? extends RuntimeException> notOwned) {
    Optional<T> existing = findOwned.get();
    if (existing.isPresent()) {
      return existing.get();
    }
    T created = create.get();
    if (insertIfAbsent.test(created)) {
      return created;
    }
    return findOwned.get().orElseThrow(notOwned);
  }
}

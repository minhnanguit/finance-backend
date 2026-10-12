package com.uit.finance.shared.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SyncDataTest {

  private static final Set<String> FIELDS =
      Set.of("name", "amountMinor", "count", "archived", "occurredOn", "occurredAt", "id");

  private static SyncData data(Object... pairs) {
    Map<String, Object> map = new HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put((String) pairs[i], pairs[i + 1]);
    }
    return SyncData.read(map, FIELDS);
  }

  private static void assertCode(ThrowingCallable call, String code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(SyncDataException.class, e -> assertThat(e.code()).isEqualTo(code));
  }

  @Test
  @DisplayName("field ngoài danh sách, kể cả field hệ thống, là sync.unknown_field (B3)")
  void unknownFieldsAreRejected() {
    assertCode(() -> data("userId", "x"), SyncDataException.UNKNOWN_FIELD);
    assertCode(() -> data("changeSeq", 1), SyncDataException.UNKNOWN_FIELD);
    assertCode(() -> SyncData.read(null, FIELDS), SyncDataException.INVALID_FIELD);
  }

  @Test
  @DisplayName("số nguyên: nhận Integer/Long/BigInteger vừa long; từ chối số thập phân thay vì cắt")
  void integersAreStrict() {
    assertThat(data("amountMinor", 5).requiredLong("amountMinor")).isEqualTo(5);
    assertThat(data("amountMinor", 1_000_000_000_000_000L).requiredLong("amountMinor"))
        .isEqualTo(1_000_000_000_000_000L);
    assertThat(data("amountMinor", BigInteger.TEN).requiredLong("amountMinor")).isEqualTo(10);

    assertCode(
        () -> data("amountMinor", 1.9).requiredLong("amountMinor"),
        SyncDataException.INVALID_FIELD);
    assertCode(
        () -> data("amountMinor", "5").requiredLong("amountMinor"),
        SyncDataException.INVALID_FIELD);
    assertCode(() -> data().requiredLong("amountMinor"), SyncDataException.INVALID_FIELD);
    assertCode(
        () -> data("amountMinor", BigInteger.TWO.pow(64)).requiredLong("amountMinor"),
        SyncDataException.INVALID_FIELD);
    assertCode(
        () -> data("count", 3_000_000_000L).requiredInt("count"), SyncDataException.INVALID_FIELD);
  }

  @Test
  @DisplayName(
      "chuỗi, boolean, uuid, ngày, thời điểm: sai kiểu là sync.invalid_field; thiếu/null là null")
  void typedReads() {
    assertThat(data("name", null).text("name")).isNull();
    assertThat(data().text("name")).isNull();
    assertCode(() -> data("name", 5).text("name"), SyncDataException.INVALID_FIELD);

    assertThat(data("archived", true).requiredBoolean("archived")).isTrue();
    assertCode(
        () -> data("archived", "true").requiredBoolean("archived"),
        SyncDataException.INVALID_FIELD);

    assertCode(() -> data("id", "not-a-uuid").uuid("id"), SyncDataException.INVALID_FIELD);

    assertThat(data("occurredOn", "2026-10-05").date("occurredOn"))
        .isEqualTo(LocalDate.of(2026, 10, 5));
    assertCode(
        () -> data("occurredOn", "05/10/2026").date("occurredOn"), SyncDataException.INVALID_FIELD);

    assertThat(data("occurredAt", "2026-10-05T08:30:00+07:00").instant("occurredAt"))
        .isEqualTo(Instant.parse("2026-10-05T01:30:00Z"));
    assertCode(
        () -> data("occurredAt", "2026-10-05T08:30:00").instant("occurredAt"),
        SyncDataException.INVALID_FIELD);
  }

  @Test
  @DisplayName("message chỉ có tên field, không có giá trị (B8)")
  void messagesNeverCarryValues() {
    assertThatThrownBy(() -> data("name", 123456).text("name"))
        .hasMessageContaining("name")
        .hasMessageNotContaining("123456");
  }
}

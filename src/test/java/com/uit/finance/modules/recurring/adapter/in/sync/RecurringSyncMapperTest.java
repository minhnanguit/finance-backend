package com.uit.finance.modules.recurring.adapter.in.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.finance.modules.recurring.application.port.in.RecurringFields;
import com.uit.finance.modules.recurring.domain.exception.ImmutableRecurringFieldException;
import com.uit.finance.modules.recurring.domain.exception.InvalidRecurringFieldException;
import com.uit.finance.modules.recurring.domain.exception.RecurringReferencePendingException;
import com.uit.finance.modules.recurring.domain.exception.RevisionConflictException;
import com.uit.finance.shared.sync.SyncDataException;
import com.uit.finance.shared.sync.SyncOutcome;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecurringSyncMapperTest {

  private static Map<String, Object> revision() {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("id", UUID.randomUUID().toString());
    r.put("effectiveFrom", "2026-11-10");
    r.put("anchorDate", "2026-11-10");
    r.put("accountId", UUID.randomUUID().toString());
    r.put("categoryId", UUID.randomUUID().toString());
    r.put("amountMinor", 3_000_000);
    r.put("note", null);
    r.put("frequency", "MONTHLY");
    r.put("interval", 1);
    r.put("weekday", null);
    r.put("month", null);
    r.put("dayOfMonth", 5);
    r.put("autoConfirm", false);
    return r;
  }

  private static Map<String, Object> data() {
    Map<String, Object> d = new HashMap<>();
    d.put("name", "Tiền nhà");
    d.put("type", "EXPENSE");
    d.put("currency", "VND");
    d.put("generationNotBefore", "2026-11-10");
    d.put("generationEndDate", null);
    d.put("remindDaysBefore", 2);
    d.put("revision", 0);
    d.put("revisions", List.of(revision()));
    return d;
  }

  @Test
  @DisplayName("đọc đủ field, kể cả field nullable của phiên bản")
  void readsEverything() {
    RecurringFields fields = RecurringSyncMapper.read(data());

    assertThat(fields.remindDaysBefore()).isEqualTo(2);
    assertThat(fields.revisions()).hasSize(1);
    assertThat(fields.revisions().getFirst().dayOfMonth()).isEqualTo(5);
    assertThat(fields.revisions().getFirst().weekday()).isNull();
  }

  @Test
  @DisplayName("field lạ ở khoản hoặc ở phiên bản (userId, changeSeq…) → sync.unknown_field")
  void unknownFieldsAreRejected() {
    Map<String, Object> withUser = data();
    withUser.put("userId", UUID.randomUUID().toString());
    Map<String, Object> rev = revision();
    rev.put("changeSeq", 1);
    Map<String, Object> withRevField = data();
    withRevField.put("revisions", List.of(rev));

    assertThatThrownBy(() -> RecurringSyncMapper.read(withUser))
        .isInstanceOfSatisfying(
            SyncDataException.class,
            e -> assertThat(e.code()).isEqualTo(SyncDataException.UNKNOWN_FIELD));
    assertThatThrownBy(() -> RecurringSyncMapper.read(withRevField))
        .isInstanceOf(SyncDataException.class);
  }

  @Test
  @DisplayName("revisions không phải mảng object, thiếu revisions, số tiền thập phân → bị từ chối")
  void wrongShapes() {
    Map<String, Object> notArray = data();
    notArray.put("revisions", "x");
    Map<String, Object> missing = data();
    missing.remove("revisions");
    Map<String, Object> rev = revision();
    rev.put("amountMinor", 1.5);
    Map<String, Object> decimal = data();
    decimal.put("revisions", List.of(rev));

    assertThatThrownBy(() -> RecurringSyncMapper.read(notArray))
        .isInstanceOf(SyncDataException.class);
    assertThatThrownBy(() -> RecurringSyncMapper.read(missing))
        .isInstanceOf(InvalidRecurringFieldException.class);
    assertThatThrownBy(() -> RecurringSyncMapper.read(decimal))
        .isInstanceOf(SyncDataException.class);
  }

  @Test
  @DisplayName("bảng dịch outcome theo ADR-009 §6")
  void verdicts() {
    assertThat(RecurringSyncVerdicts.fromCode(RecurringReferencePendingException.CODE).outcome())
        .isEqualTo(SyncOutcome.RETRY);
    assertThat(RecurringSyncVerdicts.fromCode(RevisionConflictException.CODE).outcome())
        .isEqualTo(SyncOutcome.CONFLICT);
    assertThat(RecurringSyncVerdicts.fromCode(ImmutableRecurringFieldException.CODE).outcome())
        .isEqualTo(SyncOutcome.REJECTED);
  }

  @Test
  @DisplayName("tập field khớp đúng RecurringData và RecurrenceRevisionData trong api/openapi.yaml")
  void fieldsMatchTheContract() throws IOException {
    String spec = Files.readString(Path.of("api/openapi.yaml"));

    assertThat(RecurringSyncMapper.FIELDS).isEqualTo(propertiesOf(spec, "RecurringData"));
    assertThat(RecurringSyncMapper.REVISION_FIELDS)
        .isEqualTo(propertiesOf(spec, "RecurrenceRevisionData"));
  }

  /** Tên property cấp 1 của một schema trong components (thụt 8 khoảng trắng). */
  private static java.util.Set<String> propertiesOf(String spec, String schema) {
    String normalized = spec.replace("\r\n", "\n");
    int start = normalized.indexOf("\n    " + schema + ":\n");
    int props = normalized.indexOf("      properties:\n", start);
    int end = normalized.indexOf("\n    ", props + 1);
    while (end > 0 && normalized.startsWith("\n     ", end)) {
      end = normalized.indexOf("\n    ", end + 1);
    }
    String block = normalized.substring(props, end < 0 ? normalized.length() : end);
    java.util.Set<String> names = new java.util.HashSet<>();
    Matcher m = Pattern.compile("(?m)^        ([A-Za-z]+):").matcher(block);
    while (m.find()) {
      names.add(m.group(1));
    }
    return names;
  }
}

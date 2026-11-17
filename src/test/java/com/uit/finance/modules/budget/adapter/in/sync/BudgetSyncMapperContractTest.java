package com.uit.finance.modules.budget.adapter.in.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uit.finance.api.v1.model.BudgetData;
import com.uit.finance.modules.budget.application.port.in.BudgetChangeFeedUseCase.BudgetChange;
import com.uit.finance.modules.budget.application.port.in.BudgetFields;
import com.uit.finance.modules.budget.application.port.in.BudgetView;
import com.uit.finance.shared.kernel.DomainException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Chặn lệch giữa mapper viết tay và schema {@code BudgetData} của contract (ADR-006 B3). */
class BudgetSyncMapperContractTest {

  private static final UUID FOOD = UUID.randomUUID();

  /** Tên field của schema, đọc từ model sinh ra từ {@code api/openapi.yaml}. */
  private static Set<String> contractFields() {
    return Arrays.stream(BudgetData.class.getDeclaredMethods())
        .map(method -> method.getAnnotation(JsonProperty.class))
        .filter(Objects::nonNull)
        .map(JsonProperty::value)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static Map<String, Object> data() {
    Map<String, Object> data = new HashMap<>();
    data.put("categoryId", FOOD.toString());
    data.put("currency", "VND");
    data.put("amountMinor", 3_000_000);
    data.put("startMonth", "2026-10-01");
    return data;
  }

  private static String codeOf(Map<String, Object> data) {
    Throwable thrown = catchThrowable(() -> BudgetSyncMapper.read(data));
    assertThat(thrown).isInstanceOf(DomainException.class);
    return ((DomainException) thrown).code();
  }

  @Test
  @DisplayName("field nhận vào và field trả ra khớp đúng BudgetData")
  void matchesContract() {
    BudgetView view =
        new BudgetView(UUID.randomUUID(), FOOD, "VND", 1, LocalDate.of(2026, 10, 1), false);

    assertThat(BudgetSyncMapper.FIELDS).isEqualTo(contractFields());
    assertThat(BudgetSyncMapper.toChange(new BudgetChange(view, 1)).data().keySet())
        .isEqualTo(contractFields());
  }

  @Test
  @DisplayName("đọc đúng 4 field")
  void readsFields() {
    assertThat(BudgetSyncMapper.read(data()))
        .isEqualTo(new BudgetFields(FOOD, "VND", 3_000_000, LocalDate.of(2026, 10, 1)));
  }

  @Test
  @DisplayName(
      "field do server quản lý hay field lạ: sync.unknown_field; sai kiểu: sync.invalid_field")
  void strictRead() {
    Map<String, Object> withUser = data();
    withUser.put("userId", UUID.randomUUID().toString());
    Map<String, Object> withSeq = data();
    withSeq.put("changeSeq", 1);
    Map<String, Object> decimal = data();
    decimal.put("amountMinor", new BigDecimal("1.5"));
    Map<String, Object> missingAmount = data();
    missingAmount.remove("amountMinor");

    assertThat(codeOf(withUser)).isEqualTo("sync.unknown_field");
    assertThat(codeOf(withSeq)).isEqualTo("sync.unknown_field");
    assertThat(codeOf(decimal)).isEqualTo("sync.invalid_field");
    assertThat(codeOf(missingAmount)).isEqualTo("sync.invalid_field");
  }

  @Test
  @DisplayName("tombstone không mang data")
  void tombstoneHasNoData() {
    BudgetView deleted =
        new BudgetView(UUID.randomUUID(), FOOD, "VND", 1, LocalDate.of(2026, 10, 1), true);

    assertThat(BudgetSyncMapper.toChange(new BudgetChange(deleted, 7)).data()).isNull();
  }
}

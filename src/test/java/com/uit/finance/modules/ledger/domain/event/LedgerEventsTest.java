package com.uit.finance.modules.ledger.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Event đi qua outbox và RabbitMQ, nằm trong DB và log của consumer. Chặn ai đó thêm số tiền hay
 * ghi chú vào payload (ADR-005 §8, ADR-006 B8).
 */
class LedgerEventsTest {

  private static final List<String> ALLOWED =
      List.of("eventId", "occurredAt", "transactionId", "userId", "accountId", "occurredOn");

  @ParameterizedTest
  @ValueSource(
      classes = {TransactionRecorded.class, TransactionUpdated.class, TransactionDeleted.class})
  @DisplayName("payload chỉ có id và ngày")
  void payloadCarriesOnlyIds(Class<?> event) {
    assertThat(Arrays.stream(event.getRecordComponents()).map(RecordComponent::getName))
        .containsExactlyElementsOf(ALLOWED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"recorded", "updated", "deleted"})
  @DisplayName("routing key theo <module>.<aggregate>.<action>")
  void routingKeys(String action) {
    assertThat(List.of(TransactionRecorded.TYPE, TransactionUpdated.TYPE, TransactionDeleted.TYPE))
        .contains("ledger.transaction." + action);
  }
}

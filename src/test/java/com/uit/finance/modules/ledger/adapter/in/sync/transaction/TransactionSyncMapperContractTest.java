package com.uit.finance.modules.ledger.adapter.in.sync.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.api.v1.model.TransactionData;
import com.uit.finance.modules.ledger.adapter.in.sync.support.ContractFields;
import com.uit.finance.modules.ledger.application.port.in.transaction.TransactionChangeFeedUseCase.TransactionChange;
import com.uit.finance.modules.ledger.application.port.in.transaction.TransactionView;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransactionSyncMapperContractTest {

  @Test
  @DisplayName("field nhận vào và field trả ra khớp đúng TransactionData")
  void matchesContract() {
    TransactionView view =
        new TransactionView(
            UUID.randomUUID(),
            "EXPENSE",
            "CONFIRMED",
            UUID.randomUUID(),
            null,
            50_000,
            "VND",
            UUID.randomUUID(),
            LocalDate.of(2026, 10, 5),
            null,
            null,
            null,
            false);

    assertThat(TransactionSyncMapper.INPUT_FIELDS)
        .isEqualTo(ContractFields.of(TransactionData.class));
    assertThat(TransactionSyncMapper.toChange(new TransactionChange(view, 1)).data().keySet())
        .isEqualTo(ContractFields.of(TransactionData.class));
  }
}

package com.uit.finance.modules.ledger.adapter.in.sync.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.api.v1.model.AccountData;
import com.uit.finance.modules.ledger.adapter.in.sync.support.ContractFields;
import com.uit.finance.modules.ledger.application.port.in.account.AccountChangeFeedUseCase.AccountChange;
import com.uit.finance.modules.ledger.application.port.in.account.AccountView;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Chặn lệch giữa mapper viết tay và schema {@code AccountData} của contract. */
class AccountSyncMapperContractTest {

  @Test
  @DisplayName("field nhận vào và field trả ra khớp đúng AccountData")
  void matchesContract() {
    AccountView view = new AccountView(UUID.randomUUID(), "Ví", "CASH", "VND", 0, 0, null, false);

    assertThat(AccountSyncMapper.INPUT_FIELDS).isEqualTo(ContractFields.of(AccountData.class));
    assertThat(AccountSyncMapper.toChange(new AccountChange(view, 1)).data().keySet())
        .isEqualTo(ContractFields.of(AccountData.class));
  }
}

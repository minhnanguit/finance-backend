package com.uit.finance.modules.ledger.adapter.in.sync.account;

import com.uit.finance.modules.ledger.application.port.in.account.AccountChangeFeedUseCase.AccountChange;
import com.uit.finance.modules.ledger.application.port.in.account.AccountFields;
import com.uit.finance.modules.ledger.application.port.in.account.AccountView;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerEntity;
import com.uit.finance.shared.sync.SyncChange;
import com.uit.finance.shared.sync.SyncData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** {@code data} của op ↔ dữ liệu ví, theo schema {@code AccountData} của contract. */
final class AccountSyncMapper {

  static final String ENTITY = LedgerEntity.ACCOUNT.wireName();

  /** Field client được gửi. Mọi field khác, kể cả field hệ thống, là {@code sync.unknown_field}. */
  static final Set<String> INPUT_FIELDS =
      Set.of("name", "type", "currency", "openingBalanceMinor", "sortOrder", "archived");

  private AccountSyncMapper() {}

  record Input(AccountFields fields, boolean archived) {}

  static Input read(@Nullable Map<String, Object> raw) {
    SyncData data = SyncData.read(raw, INPUT_FIELDS);
    return new Input(
        new AccountFields(
            data.text("name"),
            data.text("type"),
            data.text("currency"),
            data.requiredLong("openingBalanceMinor"),
            data.requiredInt("sortOrder")),
        data.requiredBoolean("archived"));
  }

  static SyncChange toChange(AccountChange change) {
    AccountView account = change.account();
    return new SyncChange(
        ENTITY, account.id(), change.changeSeq(), account.deleted(), toData(account));
  }

  private static Map<String, Object> toData(AccountView account) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", account.name());
    data.put("type", account.type());
    data.put("currency", account.currency());
    data.put("openingBalanceMinor", account.openingBalanceMinor());
    data.put("sortOrder", account.sortOrder());
    data.put("archived", account.archivedAt() != null);
    return data;
  }
}

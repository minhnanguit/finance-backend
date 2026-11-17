package com.uit.finance.modules.ledger.adapter.in.sync.transaction;

import com.uit.finance.modules.ledger.application.port.in.transaction.TransactionChangeFeedUseCase.TransactionChange;
import com.uit.finance.modules.ledger.application.port.in.transaction.TransactionFields;
import com.uit.finance.modules.ledger.application.port.in.transaction.TransactionView;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerEntity;
import com.uit.finance.shared.sync.SyncChange;
import com.uit.finance.shared.sync.SyncData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** {@code data} của op ↔ dữ liệu giao dịch, theo schema {@code TransactionData} của contract. */
final class TransactionSyncMapper {

  static final String ENTITY = LedgerEntity.TRANSACTION.wireName();

  static final Set<String> INPUT_FIELDS =
      Set.of(
          "type",
          "status",
          "accountId",
          "counterAccountId",
          "amountMinor",
          "currency",
          "categoryId",
          "occurredOn",
          "occurredAt",
          "payee",
          "note");

  private TransactionSyncMapper() {}

  static TransactionFields read(@Nullable Map<String, Object> raw) {
    SyncData data = SyncData.read(raw, INPUT_FIELDS);
    return new TransactionFields(
        data.text("type"),
        data.text("status"),
        data.uuid("accountId"),
        data.uuid("counterAccountId"),
        data.requiredLong("amountMinor"),
        data.text("currency"),
        data.uuid("categoryId"),
        data.date("occurredOn"),
        data.instant("occurredAt"),
        data.text("payee"),
        data.text("note"));
  }

  static SyncChange toChange(TransactionChange change) {
    TransactionView transaction = change.transaction();
    return new SyncChange(
        ENTITY, transaction.id(), change.changeSeq(), transaction.deleted(), toData(transaction));
  }

  private static Map<String, Object> toData(TransactionView transaction) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("type", transaction.type());
    data.put("status", transaction.status());
    data.put("accountId", transaction.accountId().toString());
    data.put("counterAccountId", text(transaction.counterAccountId()));
    data.put("amountMinor", transaction.amountMinor());
    data.put("currency", transaction.currency());
    data.put("categoryId", text(transaction.categoryId()));
    data.put("occurredOn", transaction.occurredOn().toString());
    data.put("occurredAt", text(transaction.occurredAt()));
    data.put("payee", transaction.payee());
    data.put("note", transaction.note());
    return data;
  }

  private static @Nullable String text(@Nullable Object value) {
    return Objects.toString(value, null);
  }
}

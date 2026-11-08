package com.uit.finance.modules.budget.adapter.in.sync;

import com.uit.finance.modules.budget.application.port.in.BudgetChangeFeedUseCase.BudgetChange;
import com.uit.finance.modules.budget.application.port.in.BudgetFields;
import com.uit.finance.modules.budget.application.port.in.BudgetView;
import com.uit.finance.shared.sync.SyncChange;
import com.uit.finance.shared.sync.SyncData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** {@code data} của op ↔ dữ liệu ngân sách, theo schema {@code BudgetData} của contract. */
final class BudgetSyncMapper {

  static final String ENTITY = "budget";

  /** Mọi field đi cả 2 chiều; field do server quản lý gửi lên là field lạ (B3). */
  static final Set<String> FIELDS = Set.of("categoryId", "currency", "amountMinor", "startMonth");

  private BudgetSyncMapper() {}

  static BudgetFields read(@Nullable Map<String, Object> raw) {
    SyncData data = SyncData.read(raw, FIELDS);
    return new BudgetFields(
        data.uuid("categoryId"),
        data.text("currency"),
        data.requiredLong("amountMinor"),
        data.date("startMonth"));
  }

  static SyncChange toChange(BudgetChange change) {
    BudgetView budget = change.budget();
    return new SyncChange(
        ENTITY, budget.id(), change.changeSeq(), budget.deleted(), toData(budget));
  }

  private static Map<String, Object> toData(BudgetView budget) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("categoryId", budget.categoryId().toString());
    data.put("currency", budget.currency());
    data.put("amountMinor", budget.amountMinor());
    data.put("startMonth", budget.startMonth().toString());
    return data;
  }
}

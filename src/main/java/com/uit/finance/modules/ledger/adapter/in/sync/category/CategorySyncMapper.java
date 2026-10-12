package com.uit.finance.modules.ledger.adapter.in.sync.category;

import com.uit.finance.modules.ledger.application.port.in.category.CategoryChangeFeedUseCase.CategoryChange;
import com.uit.finance.modules.ledger.application.port.in.category.CategoryFields;
import com.uit.finance.modules.ledger.application.port.in.category.CategoryView;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerEntity;
import com.uit.finance.shared.sync.SyncChange;
import com.uit.finance.shared.sync.SyncData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** {@code data} của op ↔ dữ liệu danh mục, theo schema {@code CategoryData} của contract. */
final class CategorySyncMapper {

  static final String ENTITY = LedgerEntity.CATEGORY.wireName();

  /** {@code templateKey} chỉ đi chiều server → client: client gửi lên là field lạ. */
  static final Set<String> INPUT_FIELDS =
      Set.of("kind", "name", "parentId", "icon", "color", "archived");

  private CategorySyncMapper() {}

  record Input(CategoryFields fields, boolean archived) {}

  static Input read(@Nullable Map<String, Object> raw) {
    SyncData data = SyncData.read(raw, INPUT_FIELDS);
    return new Input(
        new CategoryFields(
            data.text("kind"),
            data.text("name"),
            data.uuid("parentId"),
            data.text("icon"),
            data.text("color")),
        data.requiredBoolean("archived"));
  }

  static SyncChange toChange(CategoryChange change) {
    CategoryView category = change.category();
    return new SyncChange(
        ENTITY, category.id(), change.changeSeq(), category.deleted(), toData(category));
  }

  private static Map<String, Object> toData(CategoryView category) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("kind", category.kind());
    data.put("name", category.name());
    data.put("parentId", category.parentId() == null ? null : category.parentId().toString());
    data.put("icon", category.icon());
    data.put("color", category.color());
    data.put("archived", category.archivedAt() != null);
    data.put("templateKey", category.templateKey());
    return data;
  }
}

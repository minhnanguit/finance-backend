package com.uit.finance.modules.ledger.domain.model;

import com.uit.finance.shared.kernel.Ensure;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Phần client được sửa của một danh mục. {@code kind} không nằm đây vì nó bất biến. */
public record CategoryDetails(
    LedgerName name,
    @Nullable CategoryId parentId,
    @Nullable IconName icon,
    @Nullable ColorHex color) {

  public CategoryDetails {
    Ensure.notNull(name, "name");
  }

  public static CategoryDetails parse(
      @Nullable String name,
      @Nullable UUID parentId,
      @Nullable String icon,
      @Nullable String color) {
    return new CategoryDetails(
        new LedgerName(name),
        CategoryId.ofNullable(parentId),
        IconName.parse(icon),
        ColorHex.parse(color));
  }
}

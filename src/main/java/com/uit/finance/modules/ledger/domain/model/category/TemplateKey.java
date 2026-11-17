package com.uit.finance.modules.ledger.domain.model.category;

import com.uit.finance.shared.kernel.Ensure;
import java.util.regex.Pattern;

/** Mã mẫu của danh mục mặc định, ví dụ {@code fee}. Chỉ server đặt, client không gửi được. */
public record TemplateKey(String value) {

  private static final Pattern SHAPE = Pattern.compile("[a-z][a-z_]{0,39}");

  public TemplateKey {
    Ensure.notNull(value, "templateKey");
    if (!SHAPE.matcher(value).matches()) {
      throw new IllegalArgumentException("templateKey is malformed");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}

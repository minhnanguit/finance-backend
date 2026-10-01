package com.uit.finance.modules.ledger.domain.model.transaction;

import com.uit.finance.modules.ledger.domain.model.shared.Fields;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerLimits;
import org.jspecify.annotations.Nullable;

/** Người nhận hoặc nơi chi tiêu, tối đa 100 ký tự. */
public record Payee(String value) {

  public Payee {
    value = Fields.requiredText(value, LedgerLimits.MAX_PAYEE_LENGTH, "payee");
  }

  /** Rỗng hoặc chỉ có khoảng trắng nghĩa là không có người nhận. */
  public static @Nullable Payee parse(@Nullable String raw) {
    String text = Fields.optionalText(raw, LedgerLimits.MAX_PAYEE_LENGTH, "payee");
    return text == null ? null : new Payee(text);
  }

  /** Không in giá trị (ADR-006 B8). */
  @Override
  public String toString() {
    return "Payee[redacted]";
  }
}

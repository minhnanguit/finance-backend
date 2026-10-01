package com.uit.finance.modules.ledger.domain.model.transaction;

import com.uit.finance.modules.ledger.domain.model.shared.Fields;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerLimits;
import org.jspecify.annotations.Nullable;

/** Ghi chú của giao dịch, tối đa 500 ký tự. */
public record Note(String value) {

  public Note {
    value = Fields.requiredText(value, LedgerLimits.MAX_NOTE_LENGTH, "note");
  }

  /** Rỗng hoặc chỉ có khoảng trắng nghĩa là không có ghi chú. */
  public static @Nullable Note parse(@Nullable String raw) {
    String text = Fields.optionalText(raw, LedgerLimits.MAX_NOTE_LENGTH, "note");
    return text == null ? null : new Note(text);
  }

  /** Không in giá trị (ADR-006 B8). */
  @Override
  public String toString() {
    return "Note[redacted]";
  }
}

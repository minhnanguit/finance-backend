package com.uit.finance.modules.budget.application.port.out;

import com.uit.finance.shared.kernel.UserId;

public interface BudgetCountPort {

  /** Số ngân sách chưa xoá của user (giới hạn ADR-007 §4). */
  long countActive(UserId owner);
}

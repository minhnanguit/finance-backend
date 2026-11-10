package com.uit.finance.modules.recurring.application.port.out;

import com.uit.finance.shared.kernel.UserId;
import java.time.LocalDate;

public interface RecurringCountPort {

  /**
   * Số khoản còn hiệu lực của user (ADR-008 §7): chưa xoá và {@code generation_end_date} null hoặc
   * từ {@code endsNotBefore} trở đi.
   */
  long countActive(UserId owner, LocalDate endsNotBefore);
}

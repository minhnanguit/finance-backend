package com.uit.finance.modules.ledger.application.port.out.account;

import com.uit.finance.modules.ledger.domain.model.account.AccountFlows;
import com.uit.finance.shared.kernel.UserId;
import java.util.List;

public interface LoadAccountFlowsPort {

  /**
   * Tổng thu, chi, chuyển đi, chuyển đến của từng ví, bằng một câu SQL {@code SUM ... GROUP BY}.
   * Chỉ tính giao dịch {@code CONFIRMED} và chưa xoá (ADR-005 §5). Ví chưa có giao dịch nào thì
   * không cần có mặt.
   */
  List<AccountFlows> flowsByAccount(UserId owner);
}

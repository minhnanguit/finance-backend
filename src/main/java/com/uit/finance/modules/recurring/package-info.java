/**
 * Recurring: khoản thu/chi định kỳ, có timeline phiên bản và ghi có điều kiện (ADR-009).
 *
 * <p>Server chỉ lưu và kiểm lịch; giao dịch của từng kỳ do app sinh với id UUIDv5 và op {@code
 * CREATE} (ADR-009 §2, §3). Ví và danh mục được đọc qua {@code ledger::reference}, không bao giờ
 * đụng bảng của ledger (luật #6). Mọi port out nhận {@code UserId} (ADR-006 B1, ArchUnit luật #10).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Recurring",
    allowedDependencies = {"modules.ledger::reference"})
package com.uit.finance.modules.recurring;

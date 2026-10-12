/**
 * Ledger: ví, danh mục, giao dịch của từng user (ADR-005).
 *
 * <p>Số dư luôn tính ra, không lưu. Mọi port out nhận {@code UserId} để không query nào quên điều
 * kiện chủ sở hữu (ADR-006 B1, ArchUnit luật #10). Module khác biết về giao dịch qua event trong
 * {@code domain.event}; event chỉ mang id (B8).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Ledger",
    allowedDependencies = {})
package com.uit.finance.modules.ledger;

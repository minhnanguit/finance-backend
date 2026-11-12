/**
 * Budget: ngân sách theo danh mục chi, lặp lại mỗi tháng (ADR-007).
 *
 * <p>"Đã chi" không lưu ở đây: app tự tính từ giao dịch local (G6). Danh mục được đọc qua {@code
 * ledger::reference}, không bao giờ đụng bảng {@code categories} (luật #6). Mọi port out nhận
 * {@code UserId} (ADR-006 B1, ArchUnit luật #10).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Budget",
    allowedDependencies = {"modules.ledger::reference"})
package com.uit.finance.modules.budget;

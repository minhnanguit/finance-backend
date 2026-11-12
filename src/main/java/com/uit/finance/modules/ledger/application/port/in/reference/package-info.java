/**
 * Tra cứu chỉ-đọc dành cho module khác (ADR-007 §7). Module khác đọc dữ liệu ledger qua đây, không
 * bao giờ đụng bảng của ledger (luật #6). Kiểu trả về chỉ mang giá trị đơn giản để module dùng nó
 * không phụ thuộc domain model của ledger.
 */
@org.springframework.modulith.NamedInterface("reference")
package com.uit.finance.modules.ledger.application.port.in.reference;

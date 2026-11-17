/**
 * Event của ledger mà module khác được phép consume. Payload chỉ mang id và ngày, không mang số
 * tiền, ghi chú hay người nhận (ADR-005 §8, ADR-006 B8); cần chi tiết thì đọc qua {@code port/in}.
 */
@org.springframework.modulith.NamedInterface("events")
package com.uit.finance.modules.ledger.domain.event;

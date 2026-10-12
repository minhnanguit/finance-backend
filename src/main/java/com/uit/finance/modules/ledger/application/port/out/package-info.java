/**
 * Port out của ledger. **Mọi method đều nhận {@code UserId owner}** và adapter phải đặt nó vào điều
 * kiện {@code WHERE user_id = :owner} của mọi query và mọi lần ghi (ADR-006 B1). ArchUnit luật #10
 * fail build nếu một method thiếu tham số này.
 */
package com.uit.finance.modules.ledger.application.port.out;

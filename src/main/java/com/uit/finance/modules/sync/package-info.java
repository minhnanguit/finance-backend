/**
 * Sync: đồng bộ thay đổi giữa điện thoại và server (ADR-002).
 *
 * <p>Hiện mới có {@code ChangeSequencer} (Phase 2 của {@code docs/LEDGER-PLAN.md}); controller
 * push/pull, {@code sync_ops} và SPI {@code SyncHandler} làm ở Phase 3. Module này chỉ phụ thuộc
 * {@code shared}, không biết ledger hay module nào khác tồn tại.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Sync",
    allowedDependencies = {})
package com.uit.finance.modules.sync;

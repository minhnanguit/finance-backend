/**
 * Sync: đồng bộ thay đổi giữa điện thoại và server (ADR-002).
 *
 * <p>Push/pull, chống gửi trùng ({@code sync_ops}), dãy {@code change_seq} theo user. Module này
 * chỉ phụ thuộc {@code shared}: nó gọi mọi {@code SyncHandler} Spring tìm thấy và không biết ledger
 * hay module nào khác tồn tại (ADR-002 §6).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Sync",
    allowedDependencies = {})
package com.uit.finance.modules.sync;

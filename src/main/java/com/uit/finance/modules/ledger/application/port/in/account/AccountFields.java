package com.uit.finance.modules.ledger.application.port.in.account;

import org.jspecify.annotations.Nullable;

/** Dữ liệu ví client gửi lên, chưa kiểm tra. Kiểm tra nằm ở domain (ADR-005 §3). */
public record AccountFields(
    @Nullable String name,
    @Nullable String type,
    @Nullable String currency,
    long openingBalanceMinor,
    int sortOrder) {}

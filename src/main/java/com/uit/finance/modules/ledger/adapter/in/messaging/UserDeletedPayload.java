package com.uit.finance.modules.ledger.adapter.in.messaging;

import java.util.UUID;

/**
 * Bản riêng của ledger cho payload v1 của {@code identity.user.deleted}: không import class của
 * identity. Field lạ bị bỏ qua nên identity thêm field không làm gãy consumer này.
 */
record UserDeletedPayload(UUID userId) {}

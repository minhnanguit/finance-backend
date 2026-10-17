package com.uit.finance.modules.budget.adapter.in.messaging;

import java.util.UUID;

/**
 * Cách budget nhìn payload {@code ledger.category.changed} v1. Bản copy cục bộ: consumer không
 * import class của producer. Field lạ bị bỏ qua nên producer thêm field không làm hỏng consumer.
 */
record CategoryChangedPayload(UUID categoryId, UUID userId) {}

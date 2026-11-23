package com.uit.finance.modules.recurring.adapter.in.messaging;

import java.util.UUID;

/**
 * Cách recurring nhìn payload {@code identity.user.deleted} v1. Bản copy cục bộ: consumer không
 * import class của producer.
 */
record UserDeletedPayload(UUID userId) {}

package com.uit.finance.modules.sync.adapter.in.messaging;

import java.util.UUID;

/** Bản riêng của sync cho payload v1 của {@code identity.user.deleted}. Field lạ bị bỏ qua. */
record UserDeletedPayload(UUID userId) {}

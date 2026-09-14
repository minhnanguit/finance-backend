package com.mosaicglobal.finance.modules.notification.adapter.in.messaging;

import java.util.UUID;

/**
 * This module's view of the {@code identity.user.registered} v1 payload. Deliberately a local copy:
 * consumers must not import producer classes. Unknown fields are ignored so additive changes on the
 * producer side do not break us.
 */
record UserRegisteredPayload(UUID userId, String email, String displayName) {}

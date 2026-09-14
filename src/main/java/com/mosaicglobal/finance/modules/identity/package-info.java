/**
 * Identity: users, credentials and device sessions (refresh tokens).
 *
 * <p>Depends only on {@code shared}. Other modules learn about users through the events in {@code
 * domain.event} (named interface "events"), never by reading identity tables.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Identity",
    allowedDependencies = {})
package com.mosaicglobal.finance.modules.identity;

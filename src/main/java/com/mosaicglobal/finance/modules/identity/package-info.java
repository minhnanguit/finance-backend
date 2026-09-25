/**
 * Identity: the local record of each person who signs in, and the mapping from the identity
 * provider's subject to the id every other module uses.
 *
 * <p>Holds no credentials: Keycloak owns passwords, sessions and refresh tokens (ADR-004). Other
 * modules learn about users through the events in {@code domain.event} (named interface "events"),
 * never by reading identity tables.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Identity",
    allowedDependencies = {})
package com.mosaicglobal.finance.modules.identity;

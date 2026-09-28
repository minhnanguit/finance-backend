/**
 * Notification: turns domain events into messages for the user (push, e-mail).
 *
 * <p>Consumes {@code identity.user.registered} from RabbitMQ and depends only on {@code shared}: it
 * has its own copy of the payload contract instead of importing identity classes.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Notification",
    allowedDependencies = {})
package com.uit.finance.modules.notification;

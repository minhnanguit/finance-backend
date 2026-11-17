/**
 * Shared kernel and cross-cutting infrastructure. Open module: every business module may use it.
 *
 * <p>Sub-packages have different audiences, enforced by ArchUnit:
 *
 * <ul>
 *   <li>{@code kernel} – pure Java building blocks, usable from any layer including domain
 *   <li>{@code web}, {@code persistence}, {@code security}, {@code messaging} – framework adapters,
 *       usable only from adapter packages (never from domain or application)
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Shared",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.uit.finance.shared;

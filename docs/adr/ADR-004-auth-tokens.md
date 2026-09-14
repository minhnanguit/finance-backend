# ADR-004 – Self-issued RS256 access tokens + opaque rotating refresh tokens

**Status:** accepted · 2026-09-12

## Context
First-party mobile clients need a login that works with e-mail + password, supports several devices per user, and can revoke a stolen device. A full OAuth2 Authorization Server (Spring Authorization Server / Keycloak) implies browser-based authorization-code + PKCE flows and another deployable, which is more than the core needs.

## Decision
- The backend is an **OAuth2 Resource Server** (Spring Security) validating RS256 JWTs and also **mints** them (`JwtEncoder`) in `identity.adapter.out.security`. Same key pair (`app.security.jwt.*-key-pem`; ephemeral in dev).
- Access token: 15 minutes, `sub` = user id, `did` = device id, `jti`.
- Refresh token: 256-bit random, **only its SHA-256 is stored**, bound to a device, 30 days, **single use**. Refresh rotates the token; presenting an already-rotated token is treated as theft and **revokes every session of that device** (`RefreshSessionService`).
- Passwords: Spring's delegating encoder (`{bcrypt}` today), hash comparison always executed so unknown e-mails do not respond faster.
- All auth endpoints are under the same `Idempotency-Key` discipline as other writes.

## Consequences
- One deployable, no browser redirects in the mobile UX.
- Moving to an external IdP later touches `shared.security` (decoder config) and `identity.adapter.out.security` (issuer) only; the application layer speaks in ports.

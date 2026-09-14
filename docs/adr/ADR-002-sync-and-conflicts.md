# ADR-002 – Offline-first sync with client-generated ids and last-write-wins

**Status:** accepted (design) · 2026-09-12 · implementation lands with the `sync` module

## Context
Users record expenses without connectivity. The client database must be the source of truth on the device, and the server must accept writes that happened in the past, out of order, and possibly twice.

## Decision
- Every syncable entity has `id UUID` generated on the client, `version`, `updated_at` (server clock on accept), `deleted_at` for soft delete.
- Client keeps an **outbox** (`op, entity, payload, idempotency_key`); a worker pushes it in batches to `POST /api/v1/sync/push` and pulls changes since a cursor from `GET /api/v1/sync/pull?since=`.
- Each push item carries its own idempotency key; the server applies items idempotently and returns per-item outcome.
- Conflict policy for personal data: **last-write-wins by server `updated_at`**. Money-affecting aggregates (balances) are derived, never synced.
- Sync is synchronous HTTP. RabbitMQ is not in this path.

## Consequences
- Simple mental model, deterministic on both sides, no CRDT machinery.
- Shared wallets (if ever) will need per-entity policies; the per-item outcome envelope leaves room for that.

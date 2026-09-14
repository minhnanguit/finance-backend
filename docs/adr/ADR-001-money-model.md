# ADR-001 – Money is stored in minor units with an ISO-4217 currency

**Status:** accepted · 2026-09-12

## Context
A finance app must never lose or invent a đồng. Floating point cannot represent decimal amounts exactly, and mixing currencies silently produces nonsense.

## Decision
- `Money(long amountMinor, Currency currency)` in `shared.kernel`; arithmetic only between equal currencies (`CurrencyMismatchException` otherwise); overflow throws (`Math.*Exact`).
- Database columns: `amount_minor BIGINT NOT NULL`, `currency CHAR(3) NOT NULL`. Never `NUMERIC` with implicit scale, never `DOUBLE`.
- API: integers in minor units + currency code. Conversion to decimal happens only at display time, using the currency's fraction digits.
- Mobile mirrors the model (`Money(amountMinor: Long, currency: String)`).

## Consequences
- Aggregations are exact and fast (integer sums).
- Multi-currency totals require an explicit conversion step with a rate and a timestamp; that is a feature, not an accident.

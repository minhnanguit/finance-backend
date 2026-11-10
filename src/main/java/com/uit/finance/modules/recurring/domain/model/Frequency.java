package com.uit.finance.modules.recurring.domain.model;

/** Chu kỳ lặp (ADR-008 R3). Không có {@code DAILY} ở v1. */
public enum Frequency {
  WEEKLY,
  MONTHLY,
  YEARLY
}

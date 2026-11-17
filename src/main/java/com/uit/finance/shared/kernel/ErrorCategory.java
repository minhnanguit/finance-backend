package com.uit.finance.shared.kernel;

/**
 * Transport-agnostic classification of a domain failure. The web layer maps each category to an
 * HTTP status; the domain never knows about HTTP.
 */
public enum ErrorCategory {
  VALIDATION,
  NOT_FOUND,
  CONFLICT,
  AUTHENTICATION,
  FORBIDDEN,
  BUSINESS_RULE,
  /** The state the request relied on no longer exists on the server (e.g. a purged sync cursor). */
  GONE
}

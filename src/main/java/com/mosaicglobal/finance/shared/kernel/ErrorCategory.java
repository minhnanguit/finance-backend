package com.mosaicglobal.finance.shared.kernel;

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
  BUSINESS_RULE
}

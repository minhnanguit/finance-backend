package com.mosaicglobal.finance.shared.web;

import com.mosaicglobal.finance.shared.kernel.ErrorCategory;
import org.springframework.http.HttpStatus;

/** The only place that knows how a domain failure category becomes an HTTP status. */
final class HttpStatusMapping {

  private HttpStatusMapping() {}

  static HttpStatus of(ErrorCategory category) {
    return switch (category) {
      case VALIDATION -> HttpStatus.BAD_REQUEST;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case CONFLICT -> HttpStatus.CONFLICT;
      case AUTHENTICATION -> HttpStatus.UNAUTHORIZED;
      case FORBIDDEN -> HttpStatus.FORBIDDEN;
      case BUSINESS_RULE -> HttpStatus.UNPROCESSABLE_CONTENT;
    };
  }
}

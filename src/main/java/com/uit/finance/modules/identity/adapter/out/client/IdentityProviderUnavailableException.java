package com.uit.finance.modules.identity.adapter.out.client;

/**
 * Keycloak không làm được việc được nhờ. Không phải lỗi nghiệp vụ: consumer để exception bay lên,
 * broker thử lại rồi đẩy vào parking lot. Message chỉ có tên thao tác và HTTP status, không có
 * token.
 */
class IdentityProviderUnavailableException extends RuntimeException {

  IdentityProviderUnavailableException(String operation, int status) {
    super("Keycloak admin API failed: " + operation + " -> HTTP " + status);
  }
}

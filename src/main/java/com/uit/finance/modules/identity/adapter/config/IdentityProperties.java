package com.uit.finance.modules.identity.adapter.config;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param subjectCacheTtl TTL của mapping {@code sub} → user id trong cache, không đụng DB. Cũng là
 *     độ stale tối đa khi user đổi email/tên trên Keycloak.
 * @param subjectCacheSize số {@code sub} tối đa giữ in-memory — tính theo concurrent active user,
 *     không phải tổng số user.
 * @param accountStatusCacheTtl mỗi instance hỏi lại DB "tài khoản đã xoá chưa" sau chừng này
 *     (ADR-008 A6). Là thời gian tối đa một instance khác còn nhận request của tài khoản vừa xoá.
 * @param deletion thời gian chờ trước khi xoá thật dữ liệu
 * @param keycloakAdmin client gọi Keycloak Admin REST API để xoá tài khoản (ADR-008 A8)
 */
@ConfigurationProperties(prefix = "app.identity")
public record IdentityProperties(
    @DefaultValue("10m") Duration subjectCacheTtl,
    @DefaultValue("10000") long subjectCacheSize,
    @DefaultValue("30s") Duration accountStatusCacheTtl,
    @DefaultValue Deletion deletion,
    @DefaultValue KeycloakAdmin keycloakAdmin) {

  public IdentityProperties {
    // Hết thời gian chờ mà instance khác còn tin tài khoản chưa xoá thì một lần ghi tới muộn sẽ
    // sống sót sau khi dữ liệu đã bị dọn.
    if (deletion.purgeDelay().compareTo(accountStatusCacheTtl) < 0) {
      throw new IllegalArgumentException(
          "app.identity.deletion.purge-delay must be >= app.identity.account-status-cache-ttl");
    }
  }

  /**
   * @param purgeDelay từ lúc yêu cầu xoá tới lúc phát {@code identity.user.deleted}. Phải dài hơn
   *     {@code accountStatusCacheTtl} để không instance nào còn nhận ghi của tài khoản đó.
   * @param finalizeInterval job hoàn tất xoá chạy mỗi chừng này
   * @param idpRemovalInterval job xoá tài khoản Keycloak chạy mỗi chừng này: là độ trễ tối đa từ
   *     lúc bấm xoá tới lúc mật khẩu và refresh token hết dùng được
   */
  public record Deletion(
      @DefaultValue("10m") Duration purgeDelay,
      @DefaultValue("1m") Duration finalizeInterval,
      @DefaultValue("15s") Duration idpRemovalInterval) {}

  /**
   * @param enabled tắt chỉ khi chạy local không có Keycloak; production bắt buộc bật
   * @param baseUrl gốc của Keycloak mà backend gọi được (backchannel), không phải issuer công khai
   * @param clientSecret secret của client {@code finance-backend} (service account, quyền {@code
   *     manage-users}); chỉ lấy từ biến môi trường, không có giá trị mặc định — local thì {@code
   *     make up} sinh ngẫu nhiên vào {@code deploy/.env}
   */
  public record KeycloakAdmin(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("http://localhost:8082") String baseUrl,
      @DefaultValue("finance") String realm,
      @DefaultValue("finance-backend") String clientId,
      @Nullable String clientSecret,
      @DefaultValue("5s") Duration connectTimeout,
      @DefaultValue("10s") Duration readTimeout) {}
}

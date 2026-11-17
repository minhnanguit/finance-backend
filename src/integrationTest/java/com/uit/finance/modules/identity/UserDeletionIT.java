package com.uit.finance.modules.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.ApiClient;
import com.uit.finance.TestIdentityProvider;
import com.uit.finance.TestcontainersConfiguration;
import com.uit.finance.modules.identity.application.port.out.IdentityProviderAccountsPort;
import com.uit.finance.modules.identity.domain.model.ExternalSubject;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * Xoá tài khoản end-to-end (ADR-008): HTTP thật với token có chữ ký thật → job xoá tài khoản
 * Keycloak (hàng đợi {@code users.idp_removed_at}) và job hoàn tất xoá → outbox → RabbitMQ →
 * consumer của ledger, budget và sync. Chỉ thay Keycloak Admin API bằng fake ghi lại {@code sub} bị
 * xoá.
 *
 * <p>Thời gian chờ và cache trạng thái đặt về 0 để test không phải đợi 10 phút; luật "thời gian chờ
 * >= cache" vẫn được kiểm khi khởi động context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, UserDeletionIT.FakeKeycloakConfig.class})
class UserDeletionIT {

  private static final String ME = "/api/v1/me";
  private static final String TODAY = LocalDate.now(ZoneOffset.UTC).toString();

  @LocalServerPort int port;
  @Autowired JdbcClient jdbc;
  @Autowired RecordingKeycloak keycloak;

  private ApiClient api;
  private final TestIdentityProvider idp = TestIdentityProvider.instance();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    TestIdentityProvider idp = TestIdentityProvider.instance();
    registry.add("app.security.issuer-uri", () -> TestIdentityProvider.ISSUER);
    registry.add("app.security.jwk-set-uri", idp::jwkSetUri);
    registry.add("app.security.audience", () -> TestIdentityProvider.AUDIENCE);
    registry.add("app.identity.account-status-cache-ttl", () -> "0s");
    registry.add("app.identity.deletion.purge-delay", () -> "0s");
    registry.add("app.identity.deletion.finalize-interval", () -> "300ms");
    registry.add("app.identity.deletion.idp-removal-interval", () -> "200ms");
  }

  @BeforeEach
  void client() {
    api = new ApiClient(port);
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------------

  private record Account(String subject, String email, String token, UUID id) {}

  /** User mới, vừa đăng nhập (auth_time = bây giờ), đã được provision. */
  private Account freshlySignedIn() {
    String subject = "sub-" + UUID.randomUUID();
    String email = subject + "@example.com";
    String token = idp.validToken(subject, email, "Ann", Instant.now().minusSeconds(10));
    JsonNode me = api.json(api.get(ME, token));
    return new Account(subject, email, token, UUID.fromString(me.get("id").asString()));
  }

  private static String code(ResponseEntity<String> response, ApiClient api) {
    return api.json(response).get("code").asString();
  }

  private JsonNode pullAll(String token) {
    ResponseEntity<String> response = api.get("/api/v1/sync/pull?limit=500", token);
    assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
    return api.json(response).get("changes");
  }

  private static Map<String, Object> op(
      String entity, UUID id, String action, Map<String, Object> data) {
    Map<String, Object> op = new LinkedHashMap<>();
    op.put("opId", UUID.randomUUID().toString());
    op.put("entity", entity);
    op.put("id", id.toString());
    op.put("action", action);
    op.put("data", data);
    return op;
  }

  /** Một ví, một khoản chi và một ngân sách, để có dữ liệu ở ledger, budget lẫn sync_ops. */
  private void writeSomeLedgerData(Account account) {
    JsonNode categories = pullAll(account.token());
    UUID food = null;
    for (JsonNode change : categories) {
      if ("category".equals(change.get("entity").asString())
          && "Ăn uống".equals(change.get("data").get("name").asString())) {
        food = UUID.fromString(change.get("id").asString());
      }
    }
    assertThat(food).isNotNull();
    UUID wallet = UUID.randomUUID();
    Map<String, Object> walletData = new LinkedHashMap<>();
    walletData.put("name", "Ví");
    walletData.put("type", "CASH");
    walletData.put("currency", "VND");
    walletData.put("openingBalanceMinor", 0);
    walletData.put("sortOrder", 0);
    walletData.put("archived", false);
    Map<String, Object> expense = new LinkedHashMap<>();
    expense.put("type", "EXPENSE");
    expense.put("status", "CONFIRMED");
    expense.put("accountId", wallet.toString());
    expense.put("amountMinor", 50_000);
    expense.put("currency", "VND");
    expense.put("categoryId", food.toString());
    expense.put("occurredOn", TODAY);
    Map<String, Object> budget = new LinkedHashMap<>();
    budget.put("categoryId", food.toString());
    budget.put("currency", "VND");
    budget.put("amountMinor", 2_000_000);
    budget.put("startMonth", LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).toString());
    ResponseEntity<String> pushed =
        api.post(
            "/api/v1/sync/push",
            Map.of(
                "deviceId",
                "it-device",
                "ops",
                List.of(
                    op("account", wallet, "UPSERT", walletData),
                    op("transaction", UUID.randomUUID(), "UPSERT", expense),
                    op("budget", UUID.randomUUID(), "UPSERT", budget))),
            UUID.randomUUID().toString(),
            account.token());
    assertThat(pushed.getStatusCode().value()).as(pushed.getBody()).isEqualTo(200);
    api.json(pushed)
        .get("results")
        .forEach(result -> assertThat(result.get("outcome").asString()).isEqualTo("APPLIED"));
  }

  private long rowsOf(String table, UUID userId) {
    return jdbc.sql("SELECT count(*) FROM " + table + " WHERE user_id = :u")
        .param("u", userId)
        .query(Long.class)
        .single();
  }

  private String statusOf(UUID userId) {
    return jdbc.sql("SELECT status FROM users WHERE id = :u")
        .param("u", userId)
        .query(String.class)
        .single();
  }

  private @Nullable OffsetDateTime idpRemovedAt(UUID userId) {
    return jdbc.sql("SELECT idp_removed_at FROM users WHERE id = :u")
        .param("u", userId)
        .query((rs, row) -> rs.getObject("idp_removed_at", OffsetDateTime.class))
        .single();
  }

  // ---------------------------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("không vừa đăng nhập lại (thiếu auth_time hoặc quá 5 phút): 403 reauth_required")
  void deletionNeedsAFreshLogin() {
    Account ann = freshlySignedIn();
    String stale =
        idp.validToken(
            ann.subject(), ann.email(), "Ann", Instant.now().minus(Duration.ofMinutes(10)));
    String withoutAuthTime = idp.validToken(ann.subject(), ann.email(), "Ann");

    for (String token : List.of(stale, withoutAuthTime)) {
      ResponseEntity<String> response = api.delete(ME, token);
      assertThat(response.getStatusCode().value()).isEqualTo(403);
      assertThat(code(response, api)).isEqualTo("identity.reauth_required");
    }
    assertThat(statusOf(ann.id())).isEqualTo("ACTIVE");
    assertThat(api.get(ME, ann.token()).getStatusCode().value()).isEqualTo(200);
  }

  @Test
  @DisplayName(
      "xoá tài khoản: chặn mọi request ngay, xoá trên Keycloak, rồi xoá thật sổ và nhật ký sync")
  void deletingAnAccountErasesItEverywhere() {
    Account ann = freshlySignedIn();
    Account bob = freshlySignedIn();
    writeSomeLedgerData(ann);
    writeSomeLedgerData(bob);
    assertThat(rowsOf("transactions", ann.id())).isEqualTo(1);
    assertThat(rowsOf("budgets", ann.id())).isEqualTo(1);
    assertThat(rowsOf("sync_ops", ann.id())).isEqualTo(3);

    ResponseEntity<String> deleted = api.delete(ME, ann.token());

    assertThat(deleted.getStatusCode().value()).isEqualTo(202);
    assertThat(api.json(deleted).path("status").asString()).isEqualTo("DELETING");
    assertThat(api.json(deleted).path("requestedAt").asString()).isNotBlank();
    // Ngay lập tức: mọi endpoint từ chối, kể cả gọi xoá lần nữa (app coi như đã xong).
    for (ResponseEntity<String> response :
        List.of(
            api.get(ME, ann.token()),
            api.get("/api/v1/sync/pull", ann.token()),
            api.delete(ME, ann.token()))) {
      assertThat(response.getStatusCode().value()).isEqualTo(403);
      assertThat(code(response, api)).isEqualTo("identity.user_deleted");
    }
    // Thông tin cá nhân bị thay ngay trong transaction của request.
    String email =
        jdbc.sql("SELECT email FROM users WHERE id = :u")
            .param("u", ann.id())
            .query(String.class)
            .single();
    assertThat(email).isEqualTo("deleted-" + ann.id() + "@erased.invalid");

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(keycloak.deleted).contains(ExternalSubject.of(ann.subject()));
              assertThat(idpRemovedAt(ann.id())).isNotNull();
              assertThat(statusOf(ann.id())).isEqualTo("DELETED");
              for (String table :
                  List.of(
                      "accounts",
                      "categories",
                      "transactions",
                      "budgets",
                      "sync_ops",
                      "user_sync_state")) {
                assertThat(rowsOf(table, ann.id())).as(table).isZero();
              }
            });

    // Người khác không bị ảnh hưởng.
    assertThat(rowsOf("transactions", bob.id())).isEqualTo(1);
    assertThat(rowsOf("budgets", bob.id())).isEqualTo(1);
    assertThat(api.get(ME, bob.token()).getStatusCode().value()).isEqualTo(200);
    assertThat(keycloak.deleted).doesNotContain(ExternalSubject.of(bob.subject()));
  }

  @Test
  @DisplayName(
      "Keycloak lỗi lâu hơn retry của broker: job vẫn thử tiếp tới khi xoá được, không mất việc")
  void keycloakOutageDoesNotLoseTheRemoval() {
    Account ann = freshlySignedIn();
    // ~2 giây lỗi liên tiếp (job chạy mỗi 200 ms) — retry của RabbitMQ đã bỏ cuộc từ lâu ở mức này.
    keycloak.failuresLeft.set(10);

    assertThat(api.delete(ME, ann.token()).getStatusCode().value()).isEqualTo(202);

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(keycloak.failuresLeft.get()).isZero();
              assertThat(keycloak.deleted).contains(ExternalSubject.of(ann.subject()));
              assertThat(idpRemovedAt(ann.id())).isNotNull();
            });
  }

  @Test
  @DisplayName("email của tài khoản đã xoá đăng ký lại được ngay: tài khoản mới, sổ trống")
  void theSameEmailCanSignUpAgain() {
    Account ann = freshlySignedIn();
    assertThat(api.delete(ME, ann.token()).getStatusCode().value()).isEqualTo(202);

    String newSubject = "sub-" + UUID.randomUUID();
    String token = idp.validToken(newSubject, ann.email(), "Ann again");
    ResponseEntity<String> me = api.get(ME, token);

    assertThat(me.getStatusCode().value()).as(me.getBody()).isEqualTo(200);
    assertThat(api.json(me).get("id").asString()).isNotEqualTo(ann.id().toString());
    assertThat(api.json(me).get("email").asString()).isEqualTo(ann.email());
  }

  // ---------------------------------------------------------------------------------------------
  // Fake Keycloak Admin API
  // ---------------------------------------------------------------------------------------------

  @TestConfiguration(proxyBeanMethods = false)
  static class FakeKeycloakConfig {
    @Bean
    @Primary
    RecordingKeycloak recordingKeycloak() {
      return new RecordingKeycloak();
    }
  }

  static class RecordingKeycloak implements IdentityProviderAccountsPort {
    final List<ExternalSubject> deleted = new CopyOnWriteArrayList<>();

    /** Số lần gọi tiếp theo trả lỗi như khi Keycloak đang tắt. */
    final AtomicInteger failuresLeft = new AtomicInteger();

    @Override
    public boolean enabled() {
      return true;
    }

    @Override
    public void deleteAccount(ExternalSubject subject) {
      if (failuresLeft.getAndUpdate(left -> Math.max(0, left - 1)) > 0) {
        throw new IllegalStateException("Keycloak unavailable");
      }
      deleted.add(subject);
    }
  }
}

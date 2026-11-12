package com.uit.finance.modules.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.ApiClient;
import com.uit.finance.TestIdentityProvider;
import com.uit.finance.TestcontainersConfiguration;
import com.uit.finance.modules.sync.application.port.in.PurgeTombstonesUseCase;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * Dọn tombstone quá hạn + cursor hết hạn (ADR-008 T1–T4) trên Postgres thật: SQL xoá, FK trong
 * module, mốc dọn và {@code 410} ở pull. Tombstone được "làm cũ" bằng cách lùi {@code deleted_at}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class TombstonePurgeIT {

  private static final String PUSH = "/api/v1/sync/push";
  private static final String TODAY = LocalDate.now(ZoneOffset.UTC).toString();
  private static final Duration RETENTION = Duration.ofDays(90);

  @LocalServerPort int port;
  @Autowired JdbcClient jdbc;
  @Autowired PurgeTombstonesUseCase purge;

  private ApiClient api;

  @DynamicPropertySource
  static void identityProvider(DynamicPropertyRegistry registry) {
    TestIdentityProvider idp = TestIdentityProvider.instance();
    registry.add("app.security.issuer-uri", () -> TestIdentityProvider.ISSUER);
    registry.add("app.security.jwk-set-uri", idp::jwkSetUri);
    registry.add("app.security.audience", () -> TestIdentityProvider.AUDIENCE);
  }

  @BeforeEach
  void client() {
    api = new ApiClient(port);
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------------

  private record User(String token, UUID id) {}

  private User newUser() {
    String subject = "sub-" + UUID.randomUUID();
    String token =
        TestIdentityProvider.instance().validToken(subject, subject + "@example.com", "Tester");
    JsonNode me = api.json(api.get("/api/v1/me", token));
    return new User(token, UUID.fromString(me.get("id").asString()));
  }

  private ResponseEntity<String> pull(User user, String since, int limit) {
    String query = "?limit=" + limit + (since == null ? "" : "&since=" + since);
    return api.get("/api/v1/sync/pull" + query, user.token());
  }

  private record Pulled(List<JsonNode> changes, List<String> cursors) {}

  /** Pull tới hết; giữ lại cursor của từng trang. */
  private Pulled pullAll(User user, String since, int pageSize) {
    List<JsonNode> all = new ArrayList<>();
    List<String> cursors = new ArrayList<>();
    String cursor = since;
    JsonNode page;
    do {
      ResponseEntity<String> response = pull(user, cursor, pageSize);
      assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
      page = api.json(response);
      page.get("changes").forEach(all::add);
      cursor = page.get("nextCursor").asString();
      cursors.add(cursor);
    } while (page.get("hasMore").asBoolean());
    return new Pulled(all, cursors);
  }

  private void pushOk(User user, Map<String, Object>... ops) {
    ResponseEntity<String> response =
        api.post(
            PUSH,
            Map.of("deviceId", "it-device", "ops", List.of(ops)),
            UUID.randomUUID().toString(),
            user.token());
    assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
    api.json(response)
        .get("results")
        .forEach(result -> assertThat(result.get("outcome").asString()).isEqualTo("APPLIED"));
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

  private static Map<String, Object> account(UUID id) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "Ví");
    data.put("type", "CASH");
    data.put("currency", "VND");
    data.put("openingBalanceMinor", 0);
    data.put("sortOrder", 0);
    data.put("archived", false);
    return op("account", id, "UPSERT", data);
  }

  private static Map<String, Object> expense(UUID id, UUID account, UUID category) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("type", "EXPENSE");
    data.put("status", "CONFIRMED");
    data.put("accountId", account.toString());
    data.put("amountMinor", 10_000);
    data.put("currency", "VND");
    data.put("categoryId", category.toString());
    data.put("occurredOn", TODAY);
    return op("transaction", id, "UPSERT", data);
  }

  private static Map<String, Object> budget(UUID id, UUID category) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("categoryId", category.toString());
    data.put("currency", "VND");
    data.put("amountMinor", 2_000_000);
    data.put("startMonth", LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).toString());
    return op("budget", id, "UPSERT", data);
  }

  private static Map<String, Object> delete(String entity, UUID id) {
    return op(entity, id, "DELETE", null);
  }

  private UUID foodCategory(User user) {
    return pullAll(user, null, 500).changes().stream()
        .filter(change -> "category".equals(change.get("entity").asString()))
        .filter(change -> "Ăn uống".equals(change.get("data").get("name").asString()))
        .map(change -> UUID.fromString(change.get("id").asString()))
        .findFirst()
        .orElseThrow();
  }

  /** Lùi {@code deleted_at} 100 ngày để tombstone quá hạn giữ 90 ngày. */
  private void ageTombstone(String table, UUID id) {
    jdbc.sql("UPDATE " + table + " SET deleted_at = now() - interval '100 days' WHERE id = :id")
        .param("id", id)
        .update();
  }

  private boolean exists(String table, UUID id) {
    return jdbc.sql("SELECT count(*) FROM " + table + " WHERE id = :id")
            .param("id", id)
            .query(Long.class)
            .single()
        > 0;
  }

  private long changeSeq(String table, UUID id) {
    return jdbc.sql("SELECT change_seq FROM " + table + " WHERE id = :id")
        .param("id", id)
        .query(Long.class)
        .single();
  }

  private long purgedThrough(User user) {
    return jdbc.sql("SELECT purged_through_seq FROM user_sync_state WHERE user_id = :u")
        .param("u", user.id())
        .query(Long.class)
        .single();
  }

  private int runPurge() {
    return purge.purgeDeletedBefore(Instant.now().minus(RETENTION));
  }

  // ---------------------------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "dọn tombstone quá hạn: cursor cũ hơn mốc nhận 410, pull lại từ đầu thì đi hết các trang")
  void purgedTombstonesExpireOldCursors() {
    User user = newUser();
    UUID food = foodCategory(user);
    UUID wallet = UUID.randomUUID();
    UUID gone = UUID.randomUUID();
    UUID kept = UUID.randomUUID();
    pushOk(user, account(wallet), expense(gone, wallet, food), expense(kept, wallet, food));
    // Một máy đồng bộ tới đây rồi offline rất lâu.
    String offlineCursor = pullAll(user, null, 500).cursors().getLast();
    pushOk(user, delete("transaction", gone));
    long tombstoneSeq = changeSeq("transactions", gone);
    // Tombstone chưa quá hạn thì không bị dọn.
    runPurge();
    assertThat(exists("transactions", gone)).isTrue();

    ageTombstone("transactions", gone);
    runPurge();

    assertThat(exists("transactions", gone)).isFalse();
    assertThat(exists("transactions", kept)).isTrue();
    assertThat(purgedThrough(user)).isEqualTo(tombstoneSeq);

    ResponseEntity<String> stale = pull(user, offlineCursor, 100);
    assertThat(stale.getStatusCode().value()).isEqualTo(410);
    assertThat(api.json(stale).get("code").asString()).isEqualTo("sync.cursor_expired");

    // Pull lại từ đầu theo trang nhỏ: trang giữa chừng có số thấp hơn mốc vẫn hợp lệ nhờ mốc trong
    // cursor.
    Pulled fresh = pullAll(user, null, 3);
    assertThat(fresh.cursors()).anyMatch(cursor -> cursor.contains("."));
    assertThat(fresh.changes())
        .extracting(change -> change.get("id").asString())
        .contains(kept.toString(), wallet.toString())
        .doesNotContain(gone.toString());
    // Cursor cuối đã vượt mốc: dùng tiếp bình thường.
    ResponseEntity<String> next = pull(user, fresh.cursors().getLast(), 100);
    assertThat(next.getStatusCode().value()).isEqualTo(200);
    assertThat(api.json(next).get("changes")).isEmpty();
  }

  @Test
  @DisplayName("tombstone còn bị tombstone khác trỏ tới: dọn ngay sau nó trong cùng một lần chạy")
  void referencedTombstoneIsPurgedRightAfterItsReferrer() {
    User user = newUser();
    UUID food = foodCategory(user);
    UUID wallet = UUID.randomUUID();
    UUID spend = UUID.randomUUID();
    pushOk(user, account(wallet), expense(spend, wallet, food));
    pushOk(user, delete("transaction", spend), delete("account", wallet));
    long walletSeq = changeSeq("accounts", wallet);
    long spendSeq = changeSeq("transactions", spend);
    ageTombstone("transactions", spend);
    ageTombstone("accounts", wallet);

    // Một lần chạy: ví chỉ xoá được sau khi giao dịch trỏ tới nó đã xoá (FK của V4 không vỡ).
    runPurge();

    assertThat(exists("transactions", spend)).isFalse();
    assertThat(exists("accounts", wallet)).isFalse();
    assertThat(purgedThrough(user)).isEqualTo(Math.max(walletSeq, spendSeq));
  }

  @Test
  @DisplayName("dọn của user này không đụng tới user khác")
  void purgeIsScopedToTheOwner() {
    User ann = newUser();
    User bob = newUser();
    UUID annFood = foodCategory(ann);
    UUID bobFood = foodCategory(bob);
    UUID annWallet = UUID.randomUUID();
    UUID bobWallet = UUID.randomUUID();
    UUID annSpend = UUID.randomUUID();
    UUID bobSpend = UUID.randomUUID();
    pushOk(ann, account(annWallet), expense(annSpend, annWallet, annFood));
    pushOk(bob, account(bobWallet), expense(bobSpend, bobWallet, bobFood));
    pushOk(ann, delete("transaction", annSpend));
    pushOk(bob, delete("transaction", bobSpend));
    ageTombstone("transactions", annSpend);

    runPurge();

    assertThat(exists("transactions", annSpend)).isFalse();
    assertThat(exists("transactions", bobSpend)).isTrue();
    assertThat(purgedThrough(bob)).isZero();
  }

  @Test
  @DisplayName("tombstone ngân sách quá hạn được dọn cùng job, mốc của cursor tính cả nó")
  void budgetTombstonesArePurgedToo() {
    User user = newUser();
    UUID food = foodCategory(user);
    UUID gone = UUID.randomUUID();
    pushOk(user, budget(gone, food));
    String offlineCursor = pullAll(user, null, 500).cursors().getLast();
    pushOk(user, delete("budget", gone));
    long tombstoneSeq = changeSeq("budgets", gone);
    ageTombstone("budgets", gone);

    runPurge();

    assertThat(exists("budgets", gone)).isFalse();
    assertThat(purgedThrough(user)).isEqualTo(tombstoneSeq);
    assertThat(pull(user, offlineCursor, 100).getStatusCode().value()).isEqualTo(410);
    // Đã dọn thì tạo lại ngân sách cho cùng danh mục vẫn được (không vướng unique index).
    pushOk(user, budget(UUID.randomUUID(), food));
  }
}

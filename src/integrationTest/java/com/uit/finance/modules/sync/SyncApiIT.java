package com.uit.finance.modules.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.ApiClient;
import com.uit.finance.TestIdentityProvider;
import com.uit.finance.TestcontainersConfiguration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.LongStream;
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
 * Sync qua HTTP thật, token có chữ ký thật. Phủ toàn bộ "test tấn công" của Phase 3 trong {@code
 * docs/LEDGER-PLAN.md}. Mỗi test dùng user mới nên không cần dọn dữ liệu.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SyncApiIT {

  private static final String PUSH = "/api/v1/sync/push";
  private static final String TODAY = LocalDate.now(ZoneOffset.UTC).toString();

  @LocalServerPort int port;
  @Autowired JdbcClient jdbc;

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

  /** Một user Keycloak mới, đã được provision. */
  private record User(String token, UUID id) {}

  private User newUser() {
    String subject = "sub-" + UUID.randomUUID();
    String token =
        TestIdentityProvider.instance().validToken(subject, subject + "@example.com", "Tester");
    JsonNode me = api.json(api.get("/api/v1/me", token));
    return new User(token, UUID.fromString(me.get("id").asString()));
  }

  private ResponseEntity<String> push(User user, List<Map<String, Object>> ops) {
    return api.post(
        PUSH,
        Map.of("deviceId", "it-device", "ops", ops),
        UUID.randomUUID().toString(),
        user.token());
  }

  /** Push và trả mảng {@code results}; fail nếu không phải 200. */
  private JsonNode pushOk(User user, Map<String, Object>... ops) {
    ResponseEntity<String> response = push(user, List.of(ops));
    assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
    return api.json(response).get("results");
  }

  private JsonNode pull(User user, String since, int limit) {
    String query = "?limit=" + limit + (since == null ? "" : "&since=" + since);
    ResponseEntity<String> response = api.get("/api/v1/sync/pull" + query, user.token());
    assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
    return api.json(response);
  }

  /** Pull tới hết, trả mọi change theo thứ tự. */
  private List<JsonNode> pullAll(User user, int pageSize) {
    List<JsonNode> all = new ArrayList<>();
    String cursor = null;
    JsonNode page;
    do {
      page = pull(user, cursor, pageSize);
      JsonNode changes = page.get("changes");
      for (int i = 0; i < changes.size(); i++) {
        all.add(changes.get(i));
      }
      cursor = page.get("nextCursor").asString();
    } while (page.get("hasMore").asBoolean());
    return all;
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

  private static Map<String, Object> upsertAccount(UUID id) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "Ví");
    data.put("type", "CASH");
    data.put("currency", "VND");
    data.put("openingBalanceMinor", 0);
    data.put("sortOrder", 0);
    data.put("archived", false);
    return op("account", id, "UPSERT", data);
  }

  private static Map<String, Object> expenseData(UUID account, UUID category, long amount) {
    Map<String, Object> data = new HashMap<>();
    data.put("type", "EXPENSE");
    data.put("status", "CONFIRMED");
    data.put("accountId", account.toString());
    data.put("amountMinor", amount);
    data.put("currency", "VND");
    data.put("categoryId", category.toString());
    data.put("occurredOn", TODAY);
    return data;
  }

  private static Map<String, Object> upsertExpense(
      UUID id, UUID account, UUID category, long amount) {
    return op("transaction", id, "UPSERT", expenseData(account, category, amount));
  }

  private static Map<String, Object> delete(String entity, UUID id) {
    return op(entity, id, "DELETE", null);
  }

  /** Id danh mục mặc định "Ăn uống" của user: lấy từ lần pull đầu tiên. */
  private UUID foodCategory(User user) {
    return pullAll(user, 500).stream()
        .filter(change -> "category".equals(change.get("entity").asString()))
        .filter(change -> "Ăn uống".equals(change.get("data").get("name").asString()))
        .map(change -> UUID.fromString(change.get("id").asString()))
        .findFirst()
        .orElseThrow();
  }

  /** Một user đã có ví + danh mục, sẵn sàng ghi giao dịch. */
  private record Ledger(User user, UUID account, UUID food) {}

  private Ledger ledger() {
    User user = newUser();
    UUID food = foodCategory(user);
    UUID account = UUID.randomUUID();
    assertThat(pushOk(user, upsertAccount(account)).get(0).get("outcome").asString())
        .isEqualTo("APPLIED");
    return new Ledger(user, account, food);
  }

  private static String outcome(JsonNode result) {
    return result.get("outcome").asString();
  }

  private static String code(JsonNode result) {
    JsonNode code = result.get("code");
    return code == null || code.isNull() ? null : code.asString();
  }

  private List<Long> changeSeqs(UUID userId) {
    return jdbc.sql(
            """
            SELECT change_seq FROM accounts WHERE user_id = :u
            UNION ALL SELECT change_seq FROM categories WHERE user_id = :u
            UNION ALL SELECT change_seq FROM transactions WHERE user_id = :u
            ORDER BY 1
            """)
        .param("u", userId)
        .query(Long.class)
        .list();
  }

  // ---------------------------------------------------------------------------------------------
  // Lần sync đầu tiên, phân trang, cursor
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("lần sync đầu có sẵn 16 danh mục mặc định; gọi lại không tạo trùng")
  void firstSyncSeedsDefaultCategoriesOnce() {
    User ann = newUser();

    List<JsonNode> first = pullAll(ann, 200);
    List<JsonNode> again = pullAll(ann, 200);

    assertThat(first)
        .hasSize(16)
        .allSatisfy(c -> assertThat(c.get("entity").asString()).isEqualTo("category"));
    assertThat(again).hasSize(16);
    assertThat(
            jdbc.sql("SELECT count(*) FROM categories WHERE user_id = :u")
                .param("u", ann.id())
                .query(Long.class)
                .single())
        .isEqualTo(16);
  }

  @Test
  @DisplayName("phân trang: đi hết các trang thấy đủ mọi thay đổi, mỗi cái đúng một lần, tăng dần")
  void paginationIsCompleteAndOrdered() {
    Ledger ledger = ledger();
    List<Map<String, Object>> ops = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      ops.add(upsertExpense(UUID.randomUUID(), ledger.account(), ledger.food(), 1_000));
    }
    push(ledger.user(), ops);

    List<Long> seqs =
        pullAll(ledger.user(), 4).stream().map(c -> c.get("changeSeq").asLong()).toList();

    // 16 danh mục + 1 ví + 10 giao dịch
    assertThat(seqs).isEqualTo(LongStream.rangeClosed(1, 27).boxed().toList());
  }

  @Test
  @DisplayName("cursor sai định dạng → 400; cursor vượt server → 409 sync.cursor_ahead")
  void badCursors() {
    User ann = newUser();

    var malformed = api.get("/api/v1/sync/pull?since=abc", ann.token());
    var ahead = api.get("/api/v1/sync/pull?since=999999", ann.token());

    assertThat(malformed.getStatusCode().value()).isEqualTo(400);
    assertThat(api.json(malformed).get("code").asString()).isEqualTo("sync.invalid_cursor");
    assertThat(ahead.getStatusCode().value()).isEqualTo(409);
    assertThat(api.json(ahead).get("code").asString()).isEqualTo("sync.cursor_ahead");
  }

  // ---------------------------------------------------------------------------------------------
  // Test tấn công (B1, B2, B3)
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("A pull không bao giờ thấy dữ liệu của B")
  void pullNeverLeaksAnotherUsersData() {
    Ledger bob = ledger();
    UUID bobsTx = UUID.randomUUID();
    pushOk(bob.user(), upsertExpense(bobsTx, bob.account(), bob.food(), 50_000));
    User ann = newUser();

    Set<String> annSees = new HashSet<>();
    pullAll(ann, 500).forEach(c -> annSees.add(c.get("id").asString()));

    assertThat(annSees)
        .hasSize(16)
        .doesNotContain(bob.account().toString(), bobsTx.toString(), bob.food().toString());
  }

  @Test
  @DisplayName("A UPSERT với id giao dịch của B: REJECTED, current null, dữ liệu B không đổi")
  void upsertWithAnotherUsersIdIsRejected() {
    Ledger bob = ledger();
    UUID bobsTx = UUID.randomUUID();
    pushOk(bob.user(), upsertExpense(bobsTx, bob.account(), bob.food(), 50_000));
    Ledger ann = ledger();

    JsonNode result =
        pushOk(ann.user(), upsertExpense(bobsTx, ann.account(), ann.food(), 1)).get(0);

    assertThat(outcome(result)).isEqualTo("REJECTED");
    assertThat(code(result)).isEqualTo("ledger.not_found");
    assertThat(result.get("current").isNull()).isTrue();
    var row =
        jdbc.sql("SELECT user_id, amount_minor FROM transactions WHERE id = :id")
            .param("id", bobsTx)
            .query()
            .singleRow();
    assertThat(row.get("user_id")).isEqualTo(bob.user().id());
    assertThat(row.get("amount_minor")).isEqualTo(50_000L);
  }

  @Test
  @DisplayName("A trỏ giao dịch vào ví của B: RETRY, giống hệt khi ví chưa tới (B2)")
  void referenceToAnotherUsersAccountLooksLikeAMissingOne() {
    Ledger bob = ledger();
    Ledger ann = ledger();

    JsonNode foreign =
        pushOk(ann.user(), upsertExpense(UUID.randomUUID(), bob.account(), ann.food(), 1_000))
            .get(0);
    JsonNode missing =
        pushOk(ann.user(), upsertExpense(UUID.randomUUID(), UUID.randomUUID(), ann.food(), 1_000))
            .get(0);

    assertThat(outcome(foreign)).isEqualTo("RETRY");
    assertThat(code(foreign)).isEqualTo("ledger.reference_pending");
    assertThat(outcome(missing)).isEqualTo(outcome(foreign));
    assertThat(code(missing)).isEqualTo(code(foreign));
    assertThat(foreign.get("current").isNull()).isTrue();
    assertThat(missing.get("current").isNull()).isTrue();
  }

  @Test
  @DisplayName("data có userId / changeSeq / field lạ / số tiền thập phân: REJECTED")
  void serverFieldsAndUnknownFieldsAreRejected() {
    Ledger ann = ledger();
    List<String> codes = new ArrayList<>();
    for (String sneaky : List.of("userId", "changeSeq", "version", "createdAt", "isAdmin")) {
      Map<String, Object> data = expenseData(ann.account(), ann.food(), 1_000);
      data.put(sneaky, "00000000-0000-0000-0000-000000000001");
      codes.add(
          code(pushOk(ann.user(), op("transaction", UUID.randomUUID(), "UPSERT", data)).get(0)));
    }
    Map<String, Object> fractional = expenseData(ann.account(), ann.food(), 0);
    fractional.put("amountMinor", 1.5);

    JsonNode fraction =
        pushOk(ann.user(), op("transaction", UUID.randomUUID(), "UPSERT", fractional)).get(0);

    assertThat(codes).containsOnly("sync.unknown_field");
    assertThat(outcome(fraction)).isEqualTo("REJECTED");
    assertThat(code(fraction)).isEqualTo("sync.invalid_field");
    assertThat(
            jdbc.sql("SELECT count(*) FROM transactions WHERE user_id = :u")
                .param("u", ann.user().id())
                .query(Long.class)
                .single())
        .isZero();
  }

  @Test
  @DisplayName("101 op → 400; body > 256 KB → 413; số tiền > 10^15 và ghi chú 501 ký tự → REJECTED")
  void limitsAreEnforced() {
    Ledger ann = ledger();
    List<Map<String, Object>> tooMany = new ArrayList<>();
    for (int i = 0; i < 101; i++) {
      tooMany.add(delete("transaction", UUID.randomUUID()));
    }
    Map<String, Object> huge = expenseData(ann.account(), ann.food(), 1_000);
    huge.put("note", "x".repeat(300 * 1024));
    Map<String, Object> longNote = expenseData(ann.account(), ann.food(), 1_000);
    longNote.put("note", "x".repeat(501));

    var batch = push(ann.user(), tooMany);
    var body = push(ann.user(), List.of(op("transaction", UUID.randomUUID(), "UPSERT", huge)));
    JsonNode results =
        pushOk(
            ann.user(),
            upsertExpense(UUID.randomUUID(), ann.account(), ann.food(), 1_000_000_000_000_001L),
            op("transaction", UUID.randomUUID(), "UPSERT", longNote));

    assertThat(batch.getStatusCode().value()).isEqualTo(400);
    assertThat(body.getStatusCode().value()).isEqualTo(413);
    assertThat(api.json(body).get("code").asString()).isEqualTo("request.too_large");
    assertThat(outcome(results.get(0))).isEqualTo("REJECTED");
    assertThat(code(results.get(0))).isEqualTo("ledger.invalid_field");
    assertThat(outcome(results.get(1))).isEqualTo("REJECTED");
    assertThat(code(results.get(1))).isEqualTo("ledger.invalid_field");
  }

  @Test
  @DisplayName("push quá 60 lần/phút → 429 + Retry-After")
  void pushIsRateLimited() {
    User ann = newUser();
    ResponseEntity<String> last = null;
    int sent = 0;
    // Cửa sổ cố định có thể sang phút mới giữa chừng; tối đa 2 cửa sổ là chắc chắn gặp 429.
    while (sent < 121) {
      last = push(ann, List.of(delete("transaction", UUID.randomUUID())));
      sent++;
      if (last.getStatusCode().value() == 429) {
        break;
      }
    }

    assertThat(last.getStatusCode().value()).isEqualTo(429);
    assertThat(api.json(last).get("code").asString()).isEqualTo("request.rate_limited");
    assertThat(Long.parseLong(last.getHeaders().getFirst("Retry-After"))).isBetween(1L, 60L);
    assertThat(sent).isGreaterThan(60);
  }

  // ---------------------------------------------------------------------------------------------
  // Chống trùng, conflict, song song
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("gửi lại cùng opId → DUPLICATE, không tạo dòng mới")
  void sameOpIdIsDuplicate() {
    Ledger ann = ledger();
    Map<String, Object> op = upsertExpense(UUID.randomUUID(), ann.account(), ann.food(), 9_000);

    JsonNode first = pushOk(ann.user(), op).get(0);
    JsonNode again = pushOk(ann.user(), op).get(0);

    assertThat(outcome(first)).isEqualTo("APPLIED");
    assertThat(outcome(again)).isEqualTo("DUPLICATE");
    assertThat(again.get("current").get("data").get("amountMinor").asLong()).isEqualTo(9_000);
    assertThat(changeSeqs(ann.user().id())).hasSize(16 + 1 + 1);
  }

  @Test
  @DisplayName("op RETRY gửi lại sau khi ví đã tới → APPLIED, không bị coi là DUPLICATE")
  void retriedOpIsAppliedOnceItsReferenceArrives() {
    User ann = newUser();
    UUID food = foodCategory(ann);
    UUID account = UUID.randomUUID();
    Map<String, Object> expense = upsertExpense(UUID.randomUUID(), account, food, 1_000);

    JsonNode early = pushOk(ann, expense).get(0);
    pushOk(ann, upsertAccount(account));
    JsonNode later = pushOk(ann, expense).get(0);

    assertThat(outcome(early)).isEqualTo("RETRY");
    assertThat(outcome(later)).isEqualTo("APPLIED");
  }

  @Test
  @DisplayName("sửa giao dịch đã bị xoá → CONFLICT + tombstone (xoá luôn thắng)")
  void deleteWinsOverUpdate() {
    Ledger ann = ledger();
    UUID tx = UUID.randomUUID();
    pushOk(ann.user(), upsertExpense(tx, ann.account(), ann.food(), 1_000));
    pushOk(ann.user(), delete("transaction", tx));

    JsonNode result =
        pushOk(ann.user(), upsertExpense(tx, ann.account(), ann.food(), 2_000)).get(0);

    assertThat(outcome(result)).isEqualTo("CONFLICT");
    assertThat(code(result)).isEqualTo("ledger.deleted");
    assertThat(result.get("current").get("deleted").asBoolean()).isTrue();
    assertThat(result.get("current").get("data").isNull()).isTrue();
  }

  @Test
  @DisplayName("một op lỗi không làm hỏng op khác trong cùng batch, kết quả đúng thứ tự")
  void oneBadOpDoesNotSpoilTheBatch() {
    Ledger ann = ledger();
    UUID good = UUID.randomUUID();

    JsonNode results =
        pushOk(
            ann.user(),
            upsertExpense(UUID.randomUUID(), ann.account(), ann.food(), 0),
            upsertExpense(good, ann.account(), ann.food(), 1_000),
            op("budget", UUID.randomUUID(), "UPSERT", Map.of()));

    assertThat(outcome(results.get(0))).isEqualTo("REJECTED");
    assertThat(outcome(results.get(1))).isEqualTo("APPLIED");
    assertThat(results.get(1).get("current").get("id").asString()).isEqualTo(good.toString());
    assertThat(outcome(results.get(2))).isEqualTo("REJECTED");
    assertThat(code(results.get(2))).isEqualTo("sync.unknown_entity");
  }

  @Test
  @DisplayName(
      "2 request push song song của cùng user: change_seq liền mạch, không lỗ, không trùng")
  void parallelPushesGetAGaplessSequence() throws Exception {
    Ledger ann = ledger();
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Callable<JsonNode>> requests = new ArrayList<>();
      for (int r = 0; r < 2; r++) {
        List<Map<String, Object>> ops = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
          ops.add(upsertExpense(UUID.randomUUID(), ann.account(), ann.food(), 1_000));
        }
        requests.add(() -> api.json(push(ann.user(), ops)).get("results"));
      }
      for (Future<JsonNode> done : pool.invokeAll(requests)) {
        JsonNode results = done.get();
        for (int i = 0; i < results.size(); i++) {
          assertThat(outcome(results.get(i))).isEqualTo("APPLIED");
        }
      }
    } finally {
      pool.shutdown();
    }

    assertThat(changeSeqs(ann.user().id()))
        .isEqualTo(LongStream.rangeClosed(1, 16 + 1 + 20).boxed().toList());
  }

  /** Chạy các batch push của cùng một user song song (2 máy cùng sync), trả kết quả của mọi op. */
  private List<JsonNode> pushInParallel(User user, List<List<Map<String, Object>>> batches)
      throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(batches.size());
    try {
      List<Callable<JsonNode>> requests = new ArrayList<>();
      for (List<Map<String, Object>> batch : batches) {
        requests.add(() -> api.json(push(user, batch)).get("results"));
      }
      List<JsonNode> all = new ArrayList<>();
      for (Future<JsonNode> done : pool.invokeAll(requests)) {
        done.get().forEach(all::add);
      }
      return all;
    } finally {
      pool.shutdown();
    }
  }

  @Test
  @DisplayName("2 máy cùng tạo ví khi gần giới hạn: tổng không bao giờ vượt 50 (review Phase 4 #7)")
  void parallelCreatesNeverExceedTheAccountLimit() throws Exception {
    Ledger ann = ledger();
    List<Map<String, Object>> filler = new ArrayList<>();
    for (int i = 0; i < 39; i++) {
      filler.add(upsertAccount(UUID.randomUUID()));
    }
    assertThat(push(ann.user(), filler).getStatusCode().value()).isEqualTo(200); // 40 ví

    List<List<Map<String, Object>>> devices = new ArrayList<>();
    for (int device = 0; device < 2; device++) {
      List<Map<String, Object>> creates = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
        creates.add(upsertAccount(UUID.randomUUID()));
      }
      devices.add(creates);
    }
    List<JsonNode> results = pushInParallel(ann.user(), devices);

    long applied = results.stream().filter(r -> outcome(r).equals("APPLIED")).count();
    assertThat(applied).isEqualTo(10);
    assertThat(results.stream().filter(r -> outcome(r).equals("REJECTED")).map(SyncApiIT::code))
        .hasSize(10)
        .allMatch("ledger.limit_exceeded"::equals);
    assertThat(
            jdbc.sql("SELECT count(*) FROM accounts WHERE user_id = :u AND deleted_at IS NULL")
                .param("u", ann.user().id())
                .query(Long.class)
                .single())
        .isEqualTo(50);
  }

  @Test
  @DisplayName(
      "máy A xoá ví đúng lúc máy B ghi giao dịch vào ví: không bao giờ còn giao dịch sống trong ví đã xoá")
  void deleteRacingWithRecordNeverOrphansATransaction() throws Exception {
    Ledger ann = ledger();
    for (int round = 0; round < 8; round++) {
      UUID wallet = UUID.randomUUID();
      pushOk(ann.user(), upsertAccount(wallet));

      pushInParallel(
          ann.user(),
          List.of(
              List.of(delete("account", wallet)),
              List.of(upsertExpense(UUID.randomUUID(), wallet, ann.food(), 1_000))));
    }

    long orphans =
        jdbc.sql(
                """
                SELECT count(*) FROM transactions t JOIN accounts a ON a.id = t.account_id
                WHERE t.user_id = :u AND t.deleted_at IS NULL AND a.deleted_at IS NOT NULL
                """)
            .param("u", ann.user().id())
            .query(Long.class)
            .single();
    assertThat(orphans).isZero();
  }
}

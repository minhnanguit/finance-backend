package com.uit.finance.modules.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.ApiClient;
import com.uit.finance.TestIdentityProvider;
import com.uit.finance.TestcontainersConfiguration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * Entity {@code recurring} qua sync HTTP thật, token có chữ ký thật: toàn bộ "test tấn công" Phase
 * 3 của {@code RECURRING-PLAN.md} (ADR-009 §5, §6, §9). Mỗi test dùng user mới.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class RecurringSyncApiIT {

  private static final String PUSH = "/api/v1/sync/push";
  private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
  private static final String NOTE = "ghi-chu-bi-mat-recurring";

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

  // ---- Helpers --------------------------------------------------------------------------------

  private record User(String token, UUID id) {}

  /** User có sẵn ví VND, ví USD, danh mục chi và danh mục thu. */
  private record Ledger(User user, UUID vnd, UUID usd, UUID expense, UUID income) {}

  private User newUser() {
    String subject = "sub-" + UUID.randomUUID();
    String token =
        TestIdentityProvider.instance().validToken(subject, subject + "@example.com", "Tester");
    JsonNode me = api.json(api.get("/api/v1/me", token));
    return new User(token, UUID.fromString(me.get("id").asString()));
  }

  private Ledger ledger() {
    User user = newUser();
    UUID vnd = UUID.randomUUID();
    UUID usd = UUID.randomUUID();
    UUID expense = UUID.randomUUID();
    UUID income = UUID.randomUUID();
    JsonNode results =
        pushOk(
            user,
            account(vnd, "VND"),
            account(usd, "USD"),
            category(expense, "EXPENSE"),
            category(income, "INCOME"));
    results.forEach(r -> assertThat(outcome(r)).isEqualTo("APPLIED"));
    return new Ledger(user, vnd, usd, expense, income);
  }

  private ResponseEntity<String> push(User user, List<Map<String, Object>> ops) {
    return api.post(
        PUSH,
        Map.of("deviceId", "it-device", "ops", ops),
        UUID.randomUUID().toString(),
        user.token());
  }

  @SafeVarargs
  private JsonNode pushOk(User user, Map<String, Object>... ops) {
    ResponseEntity<String> response = push(user, List.of(ops));
    assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
    return api.json(response).get("results");
  }

  private JsonNode pushOne(User user, Map<String, Object> op) {
    return pushOk(user, op).get(0);
  }

  private List<JsonNode> pullAll(User user) {
    List<JsonNode> all = new ArrayList<>();
    String cursor = null;
    JsonNode page;
    do {
      String query = "?limit=500" + (cursor == null ? "" : "&since=" + cursor);
      ResponseEntity<String> response = api.get("/api/v1/sync/pull" + query, user.token());
      assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
      page = api.json(response);
      page.get("changes").forEach(all::add);
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

  private static Map<String, Object> account(UUID id, String currency) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "Ví " + currency);
    data.put("type", "CASH");
    data.put("currency", currency);
    data.put("openingBalanceMinor", 0);
    data.put("sortOrder", 0);
    data.put("archived", false);
    return op("account", id, "UPSERT", data);
  }

  private static Map<String, Object> category(UUID id, String kind) {
    Map<String, Object> data = new HashMap<>();
    data.put("kind", kind);
    data.put("name", "Danh mục " + id.toString().substring(0, 8));
    data.put("parentId", null);
    data.put("archived", false);
    return op("category", id, "UPSERT", data);
  }

  static Map<String, Object> monthlyRevision(
      UUID id, LocalDate from, UUID account, UUID category, long amount, int day) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("id", id.toString());
    r.put("effectiveFrom", from.toString());
    r.put("anchorDate", from.toString());
    r.put("accountId", account.toString());
    r.put("categoryId", category.toString());
    r.put("amountMinor", amount);
    r.put("note", NOTE);
    r.put("frequency", "MONTHLY");
    r.put("interval", 1);
    r.put("weekday", null);
    r.put("month", null);
    r.put("dayOfMonth", day);
    r.put("autoConfirm", false);
    return r;
  }

  private static Map<String, Object> recurringData(
      String type, String currency, long revision, List<Map<String, Object>> revisions) {
    Map<String, Object> data = new HashMap<>();
    data.put("name", "Tiền nhà");
    data.put("type", type);
    data.put("currency", currency);
    data.put("generationNotBefore", revisions.getFirst().get("effectiveFrom"));
    data.put("generationEndDate", null);
    data.put("remindDaysBefore", 2);
    data.put("revision", revision);
    data.put("revisions", revisions);
    return data;
  }

  private static Map<String, Object> upsertRecurring(UUID id, Map<String, Object> data) {
    return op("recurring", id, "UPSERT", data);
  }

  /** Tạo "Tiền nhà" ngày 5, bắt đầu từ 2 tháng trước; trả timeline đã gửi. */
  private List<Map<String, Object>> createRent(Ledger l, UUID id) {
    List<Map<String, Object>> timeline =
        List.of(
            monthlyRevision(
                UUID.randomUUID(), TODAY.minusMonths(2), l.vnd(), l.expense(), 3_000_000, 5));
    JsonNode result =
        pushOne(l.user(), upsertRecurring(id, recurringData("EXPENSE", "VND", 0, timeline)));
    assertThat(outcome(result)).as(result.toString()).isEqualTo("APPLIED");
    return timeline;
  }

  private static List<Map<String, Object>> plus(
      List<Map<String, Object>> timeline, Map<String, Object> extra) {
    List<Map<String, Object>> next = new ArrayList<>(timeline);
    next.add(extra);
    return next;
  }

  private static String outcome(JsonNode result) {
    return result.get("outcome").asString();
  }

  private static String code(JsonNode result) {
    JsonNode code = result.get("code");
    return code == null || code.isNull() ? null : code.asString();
  }

  // ---- Kịch bản -------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "tạo → APPLIED, current mang revision 1 và đủ timeline; pull của A thấy, của B không")
  void createAndIsolation() {
    Ledger ann = ledger();
    UUID id = UUID.randomUUID();
    createRent(ann, id);
    User bob = newUser();

    JsonNode mine =
        pullAll(ann.user()).stream()
            .filter(c -> "recurring".equals(c.get("entity").asString()))
            .findFirst()
            .orElseThrow();
    assertThat(mine.get("data").get("revision").asLong()).isEqualTo(1);
    assertThat(mine.get("data").get("revisions")).hasSize(1);
    assertThat(pullAll(bob)).noneMatch(c -> "recurring".equals(c.get("entity").asString()));
  }

  @Test
  @DisplayName("A UPSERT id của B → REJECTED recurring.not_found, bản của B không đổi")
  void foreignIdIsRejected() {
    Ledger bob = ledger();
    UUID id = UUID.randomUUID();
    createRent(bob, id);
    Ledger ann = ledger();
    List<Map<String, Object>> annTimeline =
        List.of(monthlyRevision(UUID.randomUUID(), TODAY, ann.vnd(), ann.expense(), 1, 5));

    JsonNode result =
        pushOne(ann.user(), upsertRecurring(id, recurringData("EXPENSE", "VND", 0, annTimeline)));

    assertThat(outcome(result)).isEqualTo("REJECTED");
    assertThat(code(result)).isEqualTo("recurring.not_found");
    assertThat(result.get("current").isNull()).isTrue();
    assertThat(
            jdbc.sql("SELECT user_id FROM recurrings WHERE id = :id")
                .param("id", id)
                .query(UUID.class)
                .single())
        .isEqualTo(bob.user().id());
  }

  @Test
  @DisplayName("A trỏ tới ví hoặc danh mục của B → RETRY, giống hệt chưa tới (B2)")
  void foreignReferencesLookMissing() {
    Ledger bob = ledger();
    Ledger ann = ledger();
    List<Map<String, Object>> bobsAccount =
        List.of(monthlyRevision(UUID.randomUUID(), TODAY, bob.vnd(), ann.expense(), 1, 5));
    List<Map<String, Object>> missing =
        List.of(monthlyRevision(UUID.randomUUID(), TODAY, UUID.randomUUID(), ann.expense(), 1, 5));

    JsonNode foreign =
        pushOne(
            ann.user(),
            upsertRecurring(UUID.randomUUID(), recurringData("EXPENSE", "VND", 0, bobsAccount)));
    JsonNode absent =
        pushOne(
            ann.user(),
            upsertRecurring(UUID.randomUUID(), recurringData("EXPENSE", "VND", 0, missing)));

    assertThat(outcome(foreign)).isEqualTo("RETRY");
    assertThat(code(foreign)).isEqualTo("recurring.reference_pending");
    assertThat(code(foreign)).isEqualTo(code(absent));
  }

  @Test
  @DisplayName(
      "2 UPSERT cùng revision → 1 APPLIED, 1 CONFLICT revision_conflict, timeline không trộn")
  void concurrentEditsConflict() {
    Ledger ann = ledger();
    UUID id = UUID.randomUUID();
    List<Map<String, Object>> timeline = createRent(ann, id);
    Map<String, Object> raise =
        monthlyRevision(
            UUID.randomUUID(), TODAY.plusDays(20), ann.vnd(), ann.expense(), 3_500_000, 5);
    Map<String, Object> move =
        monthlyRevision(
            UUID.randomUUID(), TODAY.plusDays(20), ann.vnd(), ann.expense(), 3_000_000, 15);

    JsonNode first =
        pushOne(
            ann.user(),
            upsertRecurring(id, recurringData("EXPENSE", "VND", 1, plus(timeline, raise))));
    JsonNode second =
        pushOne(
            ann.user(),
            upsertRecurring(id, recurringData("EXPENSE", "VND", 1, plus(timeline, move))));

    assertThat(outcome(first)).isEqualTo("APPLIED");
    assertThat(outcome(second)).isEqualTo("CONFLICT");
    assertThat(code(second)).isEqualTo("recurring.revision_conflict");
    JsonNode current = second.get("current").get("data");
    assertThat(current.get("revision").asLong()).isEqualTo(2);
    assertThat(current.get("revisions").get(1).get("amountMinor").asLong()).isEqualTo(3_500_000);
    assertThat(
            jdbc.sql("SELECT COUNT(*) FROM recurring_revisions WHERE recurring_id = :id")
                .param("id", id)
                .query(Long.class)
                .single())
        .isEqualTo(2L);
  }

  @Test
  @DisplayName("2 máy sửa song song cùng revision → đúng 1 APPLIED")
  void parallelEditsApplyOnce() throws Exception {
    Ledger ann = ledger();
    UUID id = UUID.randomUUID();
    List<Map<String, Object>> timeline = createRent(ann, id);
    List<List<Map<String, Object>>> batches = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      Map<String, Object> extra =
          monthlyRevision(
              UUID.randomUUID(), TODAY.plusDays(10), ann.vnd(), ann.expense(), 1 + i, 5);
      batches.add(
          List.of(upsertRecurring(id, recurringData("EXPENSE", "VND", 1, plus(timeline, extra)))));
    }

    ExecutorService pool = Executors.newFixedThreadPool(batches.size());
    List<String> outcomes = new ArrayList<>();
    try {
      List<Callable<JsonNode>> calls = new ArrayList<>();
      for (List<Map<String, Object>> batch : batches) {
        calls.add(() -> api.json(push(ann.user(), batch)).get("results").get(0));
      }
      for (Future<JsonNode> done : pool.invokeAll(calls)) {
        outcomes.add(outcome(done.get()));
      }
    } finally {
      pool.shutdown();
    }

    assertThat(outcomes).containsOnlyOnce("APPLIED");
    assertThat(outcomes).filteredOn(o -> !"APPLIED".equals(o)).containsOnly("CONFLICT");
  }

  @Test
  @DisplayName("sửa phiên bản đã hiệu lực, đổi type/currency → REJECTED recurring.immutable_field")
  void pastAndFixedFieldsAreImmutable() {
    Ledger ann = ledger();
    UUID id = UUID.randomUUID();
    List<Map<String, Object>> timeline = createRent(ann, id);
    Map<String, Object> edited = new LinkedHashMap<>(timeline.getFirst());
    edited.put("amountMinor", 1);

    JsonNode pastEdit =
        pushOne(
            ann.user(), upsertRecurring(id, recurringData("EXPENSE", "VND", 1, List.of(edited))));
    JsonNode typeEdit =
        pushOne(ann.user(), upsertRecurring(id, recurringData("INCOME", "VND", 1, timeline)));

    assertThat(code(pastEdit)).isEqualTo("recurring.immutable_field");
    assertThat(code(typeEdit)).isEqualTo("recurring.immutable_field");
  }

  @Test
  @DisplayName("kết thúc rồi gỡ/kéo dài → immutable_field; huỷ khoản chưa bắt đầu → APPLIED")
  void endIsOneWay() {
    Ledger ann = ledger();
    UUID id = UUID.randomUUID();
    List<Map<String, Object>> future =
        List.of(
            monthlyRevision(UUID.randomUUID(), TODAY.plusDays(30), ann.vnd(), ann.expense(), 1, 5));
    pushOne(ann.user(), upsertRecurring(id, recurringData("EXPENSE", "VND", 0, future)));
    Map<String, Object> cancel = recurringData("EXPENSE", "VND", 1, future);
    cancel.put("generationEndDate", TODAY.toString());
    Map<String, Object> reopen = recurringData("EXPENSE", "VND", 2, future);

    JsonNode cancelled = pushOne(ann.user(), upsertRecurring(id, cancel));
    JsonNode reopened = pushOne(ann.user(), upsertRecurring(id, reopen));

    assertThat(outcome(cancelled)).isEqualTo("APPLIED");
    assertThat(code(reopened)).isEqualTo("recurring.immutable_field");
  }

  @Test
  @DisplayName("tổ hợp lịch sai, interval 13, ngày 32 → REJECTED recurring.invalid_field")
  void invalidRules() {
    Ledger ann = ledger();
    Map<String, Object> weekdayOnMonthly =
        monthlyRevision(UUID.randomUUID(), TODAY, ann.vnd(), ann.expense(), 1, 5);
    weekdayOnMonthly.put("weekday", 1);
    Map<String, Object> interval13 =
        monthlyRevision(UUID.randomUUID(), TODAY, ann.vnd(), ann.expense(), 1, 5);
    interval13.put("interval", 13);
    Map<String, Object> day32 =
        monthlyRevision(UUID.randomUUID(), TODAY, ann.vnd(), ann.expense(), 1, 32);

    for (Map<String, Object> bad : List.of(weekdayOnMonthly, interval13, day32)) {
      JsonNode result =
          pushOne(
              ann.user(),
              upsertRecurring(UUID.randomUUID(), recurringData("EXPENSE", "VND", 0, List.of(bad))));
      assertThat(code(result)).as(bad.toString()).isEqualTo("recurring.invalid_field");
    }
  }

  @Test
  @DisplayName(
      "danh mục thu cho khoản chi → invalid_category; ví USD cho khoản VND → currency_mismatch")
  void referenceRules() {
    Ledger ann = ledger();
    List<Map<String, Object>> incomeCategory =
        List.of(monthlyRevision(UUID.randomUUID(), TODAY, ann.vnd(), ann.income(), 1, 5));
    List<Map<String, Object>> usdAccount =
        List.of(monthlyRevision(UUID.randomUUID(), TODAY, ann.usd(), ann.expense(), 1, 5));

    JsonNode wrongKind =
        pushOne(
            ann.user(),
            upsertRecurring(UUID.randomUUID(), recurringData("EXPENSE", "VND", 0, incomeCategory)));
    JsonNode wrongCurrency =
        pushOne(
            ann.user(),
            upsertRecurring(UUID.randomUUID(), recurringData("EXPENSE", "VND", 0, usdAccount)));

    assertThat(code(wrongKind)).isEqualTo("recurring.invalid_category");
    assertThat(code(wrongCurrency)).isEqualTo("recurring.currency_mismatch");
  }

  @Test
  @DisplayName("field lạ ở khoản hoặc phiên bản (userId, changeSeq) → REJECTED sync.unknown_field")
  void unknownFields() {
    Ledger ann = ledger();
    Map<String, Object> rev =
        monthlyRevision(UUID.randomUUID(), TODAY, ann.vnd(), ann.expense(), 1, 5);
    Map<String, Object> withUser = recurringData("EXPENSE", "VND", 0, List.of(rev));
    withUser.put("userId", ann.user().id().toString());
    Map<String, Object> revWithSeq = new LinkedHashMap<>(rev);
    revWithSeq.put("changeSeq", 9);

    assertThat(code(pushOne(ann.user(), upsertRecurring(UUID.randomUUID(), withUser))))
        .isEqualTo("sync.unknown_field");
    assertThat(
            code(
                pushOne(
                    ann.user(),
                    upsertRecurring(
                        UUID.randomUUID(),
                        recurringData("EXPENSE", "VND", 0, List.of(revWithSeq))))))
        .isEqualTo("sync.unknown_field");
  }

  @Test
  @DisplayName("xoá → tombstone; sửa sau đó → CONFLICT recurring.deleted; gửi lại opId → DUPLICATE")
  void deleteWinsAndOpIdsAreIdempotent() {
    Ledger ann = ledger();
    UUID id = UUID.randomUUID();
    List<Map<String, Object>> timeline = createRent(ann, id);
    Map<String, Object> remove = op("recurring", id, "DELETE", null);

    JsonNode deleted = pushOne(ann.user(), remove);
    JsonNode again = pushOne(ann.user(), remove);
    JsonNode edit =
        pushOne(ann.user(), upsertRecurring(id, recurringData("EXPENSE", "VND", 1, timeline)));

    assertThat(outcome(deleted)).isEqualTo("APPLIED");
    assertThat(outcome(again)).isEqualTo("DUPLICATE");
    assertThat(outcome(edit)).isEqualTo("CONFLICT");
    assertThat(code(edit)).isEqualTo("recurring.deleted");
    assertThat(edit.get("current").get("deleted").asBoolean()).isTrue();
  }

  @Test
  @DisplayName(
      "CREATE giao dịch id UUIDv5 của một kỳ hai lần → lần sau CONFLICT sync.already_exists")
  void occurrenceTransactionIsCreatedOnce() {
    Ledger ann = ledger();
    UUID id = UUID.randomUUID();
    createRent(ann, id);
    UUID occurrence =
        com.uit.finance.modules.recurring.domain.model.OccurrenceIds.of(
            com.uit.finance.modules.recurring.domain.model.RecurringId.of(id), TODAY);
    Map<String, Object> tx = new HashMap<>();
    tx.put("type", "EXPENSE");
    tx.put("status", "DRAFT");
    tx.put("accountId", ann.vnd().toString());
    tx.put("amountMinor", 3_000_000);
    tx.put("currency", "VND");
    tx.put("categoryId", ann.expense().toString());
    tx.put("occurredOn", TODAY.toString());
    tx.put("payee", "Tiền nhà");

    JsonNode fromA = pushOne(ann.user(), op("transaction", occurrence, "CREATE", tx));
    JsonNode fromB = pushOne(ann.user(), op("transaction", occurrence, "CREATE", tx));

    assertThat(outcome(fromA)).isEqualTo("APPLIED");
    assertThat(outcome(fromB)).isEqualTo("CONFLICT");
    assertThat(code(fromB)).isEqualTo("sync.already_exists");
  }

  @Test
  @DisplayName("log của push không chứa tên khoản, ghi chú hay số tiền (B8)")
  void logsCarryNoFinancialData(CapturedOutput output) {
    Ledger ann = ledger();
    createRent(ann, UUID.randomUUID());

    assertThat(output.getAll()).doesNotContain(NOTE).doesNotContain("3000000");
  }
}

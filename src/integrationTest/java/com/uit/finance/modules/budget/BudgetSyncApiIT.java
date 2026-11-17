package com.uit.finance.modules.budget;

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
 * Entity {@code budget} qua sync HTTP thật, token có chữ ký thật. Phủ toàn bộ "test tấn công" của
 * Phase 3 trong {@code docs/BUDGET-PLAN.md} (ADR-007 §5, §9). Mỗi test dùng user mới.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class BudgetSyncApiIT {

  private static final String PUSH = "/api/v1/sync/push";
  private static final LocalDate THIS_MONTH = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);

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

  private record User(String token, UUID id) {}

  private User newUser() {
    String subject = "sub-" + UUID.randomUUID();
    String token =
        TestIdentityProvider.instance().validToken(subject, subject + "@example.com", "Tester");
    JsonNode me = api.json(api.get("/api/v1/me", token));
    return new User(token, UUID.fromString(me.get("id").asString()));
  }

  private JsonNode pushOk(User user, List<Map<String, Object>> ops) {
    ResponseEntity<String> response =
        api.post(
            PUSH,
            Map.of("deviceId", "it-device", "ops", ops),
            UUID.randomUUID().toString(),
            user.token());
    assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
    return api.json(response).get("results");
  }

  @SafeVarargs
  private JsonNode pushOk(User user, Map<String, Object>... ops) {
    return pushOk(user, List.of(ops));
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

  private static Map<String, Object> upsertCategory(UUID id, String kind, UUID parentId) {
    Map<String, Object> data = new HashMap<>();
    data.put("kind", kind);
    data.put("name", "Danh mục " + id.toString().substring(0, 8));
    data.put("parentId", parentId == null ? null : parentId.toString());
    data.put("archived", false);
    return op("category", id, "UPSERT", data);
  }

  private static Map<String, Object> budgetData(
      UUID categoryId, String currency, long amount, LocalDate startMonth) {
    Map<String, Object> data = new HashMap<>();
    data.put("categoryId", categoryId.toString());
    data.put("currency", currency);
    data.put("amountMinor", amount);
    data.put("startMonth", startMonth.toString());
    return data;
  }

  private static Map<String, Object> upsertBudget(
      UUID id, UUID categoryId, String currency, long amount) {
    return op("budget", id, "UPSERT", budgetData(categoryId, currency, amount, THIS_MONTH));
  }

  private static Map<String, Object> delete(String entity, UUID id) {
    return op(entity, id, "DELETE", null);
  }

  /** Tạo danh mục chi cấp 1 qua sync, trả id. */
  private UUID rootExpense(User user) {
    UUID id = UUID.randomUUID();
    assertThat(outcome(pushOk(user, upsertCategory(id, "EXPENSE", null)).get(0)))
        .isEqualTo("APPLIED");
    return id;
  }

  private static String outcome(JsonNode result) {
    return result.get("outcome").asString();
  }

  private static String code(JsonNode result) {
    JsonNode code = result.get("code");
    return code == null || code.isNull() ? null : code.asString();
  }

  private static boolean currentIsNull(JsonNode result) {
    JsonNode current = result.get("current");
    return current == null || current.isNull();
  }

  private long amountOf(UUID budgetId) {
    return jdbc.sql("select amount_minor from budgets where id = :id")
        .param("id", budgetId)
        .query(Long.class)
        .single();
  }

  private long liveBudgets(User user) {
    return jdbc.sql("select count(*) from budgets where user_id = :u and deleted_at is null")
        .param("u", user.id())
        .query(Long.class)
        .single();
  }

  // ---------------------------------------------------------------------------------------------
  // Cô lập theo user (B1, B2)
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("A pull không bao giờ thấy ngân sách của B; thấy ngân sách của mình đúng hình dạng")
  void pullIsIsolatedPerUser() {
    User ann = newUser();
    User bob = newUser();
    UUID annsBudget = UUID.randomUUID();
    UUID bobsBudget = UUID.randomUUID();
    UUID annsFood = rootExpense(ann);
    pushOk(ann, upsertBudget(annsBudget, annsFood, "VND", 3_000_000));
    pushOk(bob, upsertBudget(bobsBudget, rootExpense(bob), "VND", 3_000_000));

    List<JsonNode> budgets =
        pullAll(ann).stream().filter(c -> "budget".equals(c.get("entity").asString())).toList();

    assertThat(budgets)
        .extracting(c -> c.get("id").asString())
        .containsExactly(annsBudget.toString());
    JsonNode data = budgets.getFirst().get("data");
    assertThat(data.get("categoryId").asString()).isEqualTo(annsFood.toString());
    assertThat(data.get("currency").asString()).isEqualTo("VND");
    assertThat(data.get("amountMinor").asLong()).isEqualTo(3_000_000);
    assertThat(data.get("startMonth").asString()).isEqualTo(THIS_MONTH.toString());
  }

  @Test
  @DisplayName("A UPSERT/DELETE với id ngân sách của B: REJECTED budget.not_found, B không đổi")
  void foreignBudgetIdIsNotFound() {
    User ann = newUser();
    User bob = newUser();
    UUID bobsBudget = UUID.randomUUID();
    pushOk(bob, upsertBudget(bobsBudget, rootExpense(bob), "VND", 3_000_000));

    JsonNode upsert = pushOk(ann, upsertBudget(bobsBudget, rootExpense(ann), "VND", 1)).get(0);
    JsonNode remove = pushOk(ann, delete("budget", bobsBudget)).get(0);

    assertThat(outcome(upsert)).isEqualTo("REJECTED");
    assertThat(code(upsert)).isEqualTo("budget.not_found");
    assertThat(currentIsNull(upsert)).isTrue();
    assertThat(code(remove)).isEqualTo("budget.not_found");
    assertThat(amountOf(bobsBudget)).isEqualTo(3_000_000);
    assertThat(liveBudgets(bob)).isEqualTo(1);
  }

  @Test
  @DisplayName("A trỏ ngân sách vào danh mục của B: RETRY, giống hệt danh mục chưa tới (B2)")
  void foreignCategoryLooksLikeAPendingOne() {
    User ann = newUser();
    User bob = newUser();
    UUID bobsFood = rootExpense(bob);

    JsonNode foreign = pushOk(ann, upsertBudget(UUID.randomUUID(), bobsFood, "VND", 1)).get(0);
    JsonNode missing =
        pushOk(ann, upsertBudget(UUID.randomUUID(), UUID.randomUUID(), "VND", 1)).get(0);

    assertThat(outcome(foreign)).isEqualTo("RETRY").isEqualTo(outcome(missing));
    assertThat(code(foreign)).isEqualTo("budget.reference_pending").isEqualTo(code(missing));
    assertThat(currentIsNull(foreign)).isTrue();
    assertThat(currentIsNull(missing)).isTrue();
    assertThat(liveBudgets(ann)).isZero();
  }

  // ---------------------------------------------------------------------------------------------
  // Dữ liệu gửi lên (B3, B4)
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("data có userId / changeSeq / version / field lạ: REJECTED sync.unknown_field")
  void serverFieldsAndUnknownFieldsAreRejected() {
    User ann = newUser();
    UUID food = rootExpense(ann);
    List<String> codes = new ArrayList<>();
    for (String sneaky : List.of("userId", "changeSeq", "version", "createdAt", "spent")) {
      Map<String, Object> data = budgetData(food, "VND", 1, THIS_MONTH);
      data.put(sneaky, "00000000-0000-0000-0000-000000000001");
      codes.add(code(pushOk(ann, op("budget", UUID.randomUUID(), "UPSERT", data)).get(0)));
    }

    assertThat(codes).containsOnly("sync.unknown_field");
    assertThat(liveBudgets(ann)).isZero();
  }

  @Test
  @DisplayName(
      "số tiền 0 hoặc > 10^15, tháng UTC + 2, tháng không phải ngày 1: budget.invalid_field")
  void outOfRangeFieldsAreRejected() {
    User ann = newUser();
    UUID food = rootExpense(ann);

    JsonNode results =
        pushOk(
            ann,
            upsertBudget(UUID.randomUUID(), food, "VND", 0),
            upsertBudget(UUID.randomUUID(), food, "VND", 1_000_000_000_000_001L),
            op(
                "budget",
                UUID.randomUUID(),
                "UPSERT",
                budgetData(food, "VND", 1, THIS_MONTH.plusMonths(2))),
            op(
                "budget",
                UUID.randomUUID(),
                "UPSERT",
                budgetData(food, "VND", 1, THIS_MONTH.plusDays(14))));

    for (JsonNode result : results) {
      assertThat(outcome(result)).isEqualTo("REJECTED");
      assertThat(code(result)).isEqualTo("budget.invalid_field");
    }
    assertThat(liveBudgets(ann)).isZero();
  }

  @Test
  @DisplayName("danh mục thu, danh mục con: REJECTED budget.invalid_category")
  void invalidCategoriesAreRejected() {
    User ann = newUser();
    UUID food = rootExpense(ann);
    UUID salary = UUID.randomUUID();
    UUID coffee = UUID.randomUUID();
    pushOk(ann, upsertCategory(salary, "INCOME", null), upsertCategory(coffee, "EXPENSE", food));

    JsonNode results =
        pushOk(
            ann,
            upsertBudget(UUID.randomUUID(), salary, "VND", 1),
            upsertBudget(UUID.randomUUID(), coffee, "VND", 1));

    assertThat(results).extracting(BudgetSyncApiIT::code).containsOnly("budget.invalid_category");
  }

  @Test
  @DisplayName(
      "đổi tiền tệ của ngân sách đã có: REJECTED budget.immutable_field, current là bản cũ")
  void immutableFieldsAreRejected() {
    User ann = newUser();
    UUID food = rootExpense(ann);
    UUID budget = UUID.randomUUID();
    pushOk(ann, upsertBudget(budget, food, "VND", 3_000_000));

    JsonNode result = pushOk(ann, upsertBudget(budget, food, "USD", 3_000_000)).get(0);

    assertThat(outcome(result)).isEqualTo("REJECTED");
    assertThat(code(result)).isEqualTo("budget.immutable_field");
    assertThat(result.get("current").get("data").get("currency").asString()).isEqualTo("VND");
  }

  // ---------------------------------------------------------------------------------------------
  // Giới hạn (B4, GS8)
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "ngân sách thứ 101: limit_reached; danh mục vừa xoá mà consumer chưa chạy: vẫn APPLIED")
  void limitCountsOnlyValidBudgets() {
    User ann = newUser();
    List<UUID> categories = new ArrayList<>();
    List<Map<String, Object>> categoryOps = new ArrayList<>();
    List<Map<String, Object>> budgetOps = new ArrayList<>();
    List<UUID> budgets = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      UUID category = UUID.randomUUID();
      UUID budget = UUID.randomUUID();
      categories.add(category);
      budgets.add(budget);
      categoryOps.add(upsertCategory(category, "EXPENSE", null));
      budgetOps.add(upsertBudget(budget, category, "VND", 1_000));
    }
    assertThat(pushOk(ann, categoryOps))
        .extracting(BudgetSyncApiIT::outcome)
        .containsOnly("APPLIED");
    assertThat(pushOk(ann, budgetOps)).extracting(BudgetSyncApiIT::outcome).containsOnly("APPLIED");
    UUID extra = rootExpense(ann);

    JsonNode overLimit = pushOk(ann, upsertBudget(UUID.randomUUID(), extra, "VND", 1_000)).get(0);

    assertThat(outcome(overLimit)).isEqualTo("REJECTED");
    assertThat(code(overLimit)).isEqualTo("budget.limit_reached");

    // Xoá thẳng trong DB: không có event, như khi consumer chưa kịp chạy hoặc message bị park.
    jdbc.sql("update categories set deleted_at = now() where id = :id")
        .param("id", categories.getFirst())
        .update();

    JsonNode afterCleanup =
        pushOk(ann, upsertBudget(UUID.randomUUID(), extra, "VND", 1_000)).get(0);

    assertThat(outcome(afterCleanup)).isEqualTo("APPLIED");
    assertThat(liveBudgets(ann)).isEqualTo(100);
    assertThat(
            jdbc.sql("select deleted_at is not null from budgets where id = :id")
                .param("id", budgets.getFirst())
                .query(Boolean.class)
                .single())
        .isTrue();
  }

  // ---------------------------------------------------------------------------------------------
  // Trùng, gửi lại, xoá thắng (G3, GS4, S4)
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("cùng danh mục, 2 tiền tệ: cả 2 APPLIED")
  void sameCategoryTwoCurrencies() {
    User ann = newUser();
    UUID food = rootExpense(ann);

    JsonNode results =
        pushOk(
            ann,
            upsertBudget(UUID.randomUUID(), food, "VND", 3_000_000),
            upsertBudget(UUID.randomUUID(), food, "USD", 100_00));

    assertThat(results).extracting(BudgetSyncApiIT::outcome).containsOnly("APPLIED");
  }

  @Test
  @DisplayName(
      "2 op cùng (danh mục, tiền tệ): 1 APPLIED, 1 REJECTED budget.duplicate, current null")
  void secondBudgetForTheSamePairIsADuplicate() {
    User ann = newUser();
    UUID food = rootExpense(ann);

    JsonNode results =
        pushOk(
            ann,
            upsertBudget(UUID.randomUUID(), food, "VND", 3_000_000),
            upsertBudget(UUID.randomUUID(), food, "VND", 5_000_000));

    assertThat(outcome(results.get(0))).isEqualTo("APPLIED");
    assertThat(outcome(results.get(1))).isEqualTo("REJECTED");
    assertThat(code(results.get(1))).isEqualTo("budget.duplicate");
    assertThat(currentIsNull(results.get(1))).as("client xoá bản local rồi pull bản kia").isTrue();
    assertThat(liveBudgets(ann)).isEqualTo(1);
  }

  @Test
  @DisplayName("gửi lại cùng opId: DUPLICATE, không tạo dòng mới")
  void resendingTheSameOpIsADuplicate() {
    User ann = newUser();
    Map<String, Object> create = upsertBudget(UUID.randomUUID(), rootExpense(ann), "VND", 1);

    JsonNode first = pushOk(ann, create).get(0);
    JsonNode second = pushOk(ann, create).get(0);

    assertThat(outcome(first)).isEqualTo("APPLIED");
    assertThat(outcome(second)).isEqualTo("DUPLICATE");
    assertThat(liveBudgets(ann)).isEqualTo(1);
  }

  @Test
  @DisplayName("sửa ngân sách đã xoá: CONFLICT budget.deleted + tombstone (xoá luôn thắng)")
  void updatingADeletedBudgetConflicts() {
    User ann = newUser();
    UUID food = rootExpense(ann);
    UUID budget = UUID.randomUUID();
    pushOk(ann, upsertBudget(budget, food, "VND", 3_000_000));
    pushOk(ann, delete("budget", budget));

    JsonNode result = pushOk(ann, upsertBudget(budget, food, "VND", 9_000_000)).get(0);

    assertThat(outcome(result)).isEqualTo("CONFLICT");
    assertThat(code(result)).isEqualTo("budget.deleted");
    assertThat(result.get("current").get("deleted").asBoolean()).isTrue();
    assertThat(amountOf(budget)).isEqualTo(3_000_000);
  }

  // ---------------------------------------------------------------------------------------------
  // Log (B8)
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("log của mọi outcome không chứa số tiền (B8)")
  void logsNeverContainAmounts(CapturedOutput output) {
    User ann = newUser();
    UUID food = rootExpense(ann);
    UUID budget = UUID.randomUUID();
    long tracer = 4_242_424_242L;
    long tooBig = 1_000_000_000_000_042L;

    pushOk(
        ann,
        upsertBudget(budget, food, "VND", tracer),
        upsertBudget(UUID.randomUUID(), food, "VND", tracer),
        upsertBudget(UUID.randomUUID(), food, "USD", tooBig),
        upsertBudget(UUID.randomUUID(), UUID.randomUUID(), "VND", tracer));
    pushOk(ann, upsertBudget(budget, food, "VND", tracer + 1), delete("budget", budget));

    assertThat(output.getAll())
        .as("output thật sự bắt được log của lần push này")
        .contains("sync push user=" + ann.id())
        .doesNotContain(Long.toString(tracer))
        .doesNotContain(Long.toString(tracer + 1))
        .doesNotContain(Long.toString(tooBig));
  }
}

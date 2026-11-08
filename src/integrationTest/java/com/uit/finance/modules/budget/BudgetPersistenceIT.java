package com.uit.finance.modules.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.finance.TestIdentityProvider;
import com.uit.finance.TestcontainersConfiguration;
import com.uit.finance.modules.budget.application.port.in.BudgetFields;
import com.uit.finance.modules.budget.application.port.in.BudgetView;
import com.uit.finance.modules.budget.application.port.in.CreateBudgetUseCase;
import com.uit.finance.modules.budget.application.port.in.CreateBudgetUseCase.CreateBudgetCommand;
import com.uit.finance.modules.budget.application.port.in.DeleteBudgetUseCase;
import com.uit.finance.modules.budget.application.port.in.DeleteBudgetUseCase.DeleteBudgetCommand;
import com.uit.finance.modules.budget.application.port.in.UpdateBudgetUseCase;
import com.uit.finance.modules.budget.application.port.in.UpdateBudgetUseCase.UpdateBudgetCommand;
import com.uit.finance.modules.budget.domain.exception.DuplicateBudgetException;
import com.uit.finance.modules.ledger.application.port.in.category.CategoryFields;
import com.uit.finance.modules.ledger.application.port.in.category.CreateCategoryUseCase;
import com.uit.finance.modules.ledger.application.port.in.category.CreateCategoryUseCase.CreateCategoryCommand;
import com.uit.finance.modules.ledger.application.port.in.category.DeleteCategoryUseCase;
import com.uit.finance.modules.ledger.application.port.in.category.DeleteCategoryUseCase.DeleteCategoryCommand;
import com.uit.finance.modules.ledger.application.port.in.category.UpdateCategoryUseCase;
import com.uit.finance.modules.ledger.application.port.in.category.UpdateCategoryUseCase.UpdateCategoryCommand;
import com.uit.finance.shared.kernel.UserId;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.LongStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Budget trên Postgres + RabbitMQ thật: Flyway V7, {@code ddl-auto=validate}, SQL của adapter, ràng
 * buộc DB, {@code change_seq}, và consumer {@code ledger.category.changed} (ADR-007 GS6, GS7). Mỗi
 * test dùng user mới nên không cần dọn dữ liệu.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BudgetPersistenceIT {

  private static final LocalDate THIS_MONTH = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
  private static final String QUEUE = "budget.category-changed";
  private static final Duration WAIT = Duration.ofSeconds(30);

  @Autowired JdbcClient jdbc;
  @Autowired RabbitTemplate rabbit;
  @Autowired AmqpAdmin amqpAdmin;
  @Autowired CreateCategoryUseCase createCategory;
  @Autowired UpdateCategoryUseCase updateCategory;
  @Autowired DeleteCategoryUseCase deleteCategory;
  @Autowired CreateBudgetUseCase createBudget;
  @Autowired UpdateBudgetUseCase updateBudget;
  @Autowired DeleteBudgetUseCase deleteBudget;

  private UserId ann;

  @DynamicPropertySource
  static void identityProvider(DynamicPropertyRegistry registry) {
    TestIdentityProvider idp = TestIdentityProvider.instance();
    registry.add("app.security.issuer-uri", () -> TestIdentityProvider.ISSUER);
    registry.add("app.security.jwk-set-uri", idp::jwkSetUri);
    registry.add("app.security.audience", () -> TestIdentityProvider.AUDIENCE);
  }

  @BeforeEach
  void freshUser() {
    ann = UserId.newId();
  }

  // ---- helpers ----

  private UUID rootExpense(UserId owner, String name) {
    return expense(owner, name, null);
  }

  private UUID expense(UserId owner, String name, UUID parentId) {
    UUID id = UUID.randomUUID();
    createCategory.create(
        new CreateCategoryCommand(
            owner, id, new CategoryFields("EXPENSE", name, parentId, null, null)));
    return id;
  }

  private BudgetView budget(UserId owner, UUID categoryId, String currency) {
    return createBudget.create(
        new CreateBudgetCommand(
            owner,
            UUID.randomUUID(),
            new BudgetFields(categoryId, currency, 3_000_000, THIS_MONTH)));
  }

  private boolean isDeleted(UUID budgetId) {
    return jdbc.sql("select deleted_at is not null from budgets where id = :id")
        .param("id", budgetId)
        .query(Boolean.class)
        .single();
  }

  private long changeSeq(UUID budgetId) {
    return jdbc.sql("select change_seq from budgets where id = :id")
        .param("id", budgetId)
        .query(Long.class)
        .single();
  }

  private long lastSeq(UserId owner) {
    return jdbc.sql("select last_seq from user_sync_state where user_id = :u")
        .param("u", owner.value())
        .query(Long.class)
        .single();
  }

  /** {@code change_seq} hiện tại của mọi dòng của user (danh mục + ngân sách), tăng dần. */
  private List<Long> sequence(UserId owner) {
    return jdbc.sql(
            """
            select change_seq from categories where user_id = :u
            union all
            select change_seq from budgets where user_id = :u
            order by 1
            """)
        .param("u", owner.value())
        .query(Long.class)
        .list();
  }

  /** Chỉ đúng khi mọi lần ghi đều là insert: dòng bị sửa mang số mới, số cũ biến mất khỏi bảng. */
  private void assertGaplessSequence(UserId owner) {
    assertThat(sequence(owner))
        .containsExactlyElementsOf(LongStream.rangeClosed(1, lastSeq(owner)).boxed().toList());
  }

  /** Luôn đúng: không 2 dòng chung một số, và dòng ghi sau cùng mang đúng {@code last_seq}. */
  private void assertConsistentSequence(UserId owner) {
    List<Long> seqs = sequence(owner);
    assertThat(seqs).doesNotHaveDuplicates();
    assertThat(seqs.getLast()).isEqualTo(lastSeq(owner));
  }

  private long parkedMessages() {
    var info = amqpAdmin.getQueueInfo(QUEUE + ".parked");
    return info == null ? 0 : info.getMessageCount();
  }

  /** Gửi thẳng một envelope {@code ledger.category.changed} vào broker, như outbox gửi. */
  private void publishCategoryChanged(UUID eventId, UserId owner, UUID categoryId) {
    String json =
        """
        {"eventId":"%s","type":"ledger.category.changed","schemaVersion":1,"occurredAt":"%s",
         "traceId":null,"payload":{"eventId":"%s","occurredAt":"%s","categoryId":"%s","userId":"%s"}}
        """
            .formatted(eventId, Instant.now(), eventId, Instant.now(), categoryId, owner.value());
    MessageProperties props = new MessageProperties();
    props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    rabbit.send(
        "finance.events",
        "ledger.category.changed",
        new Message(json.getBytes(StandardCharsets.UTF_8), props));
  }

  // ---- persistence ----

  @Test
  @DisplayName("ghi và đọc lại đúng cột; mỗi lần ghi đúng một change_seq, chung dãy với ledger")
  void roundTripAndSequence() {
    UUID food = rootExpense(ann, "Ăn uống");
    BudgetView created = budget(ann, food, "VND");
    assertGaplessSequence(ann);

    BudgetFields raised = new BudgetFields(food, "VND", 5_000_000, THIS_MONTH);
    updateBudget.update(new UpdateBudgetCommand(ann, created.id(), raised));
    updateBudget.update(new UpdateBudgetCommand(ann, created.id(), raised));
    BudgetView usd = budget(ann, food, "USD");
    deleteBudget.delete(new DeleteBudgetCommand(ann, usd.id()));
    deleteBudget.delete(new DeleteBudgetCommand(ann, usd.id()));

    // danh mục, ngân sách, sửa, ngân sách USD, xoá = 5 lần ghi; gửi lại y nguyên không tốn số.
    assertThat(lastSeq(ann)).isEqualTo(5);
    assertThat(changeSeq(created.id())).isEqualTo(3);

    var row =
        jdbc.sql(
                "select category_id, currency, amount_minor, start_month from budgets where id = :id")
            .param("id", created.id())
            .query(
                (rs, i) ->
                    List.of(
                        rs.getObject(1).toString(),
                        rs.getString(2),
                        rs.getLong(3),
                        rs.getObject(4, LocalDate.class)))
            .single();
    assertThat(row).containsExactly(food.toString(), "VND", 5_000_000L, THIS_MONTH);
    assertThat(isDeleted(usd.id())).isTrue();
    assertConsistentSequence(ann);
  }

  @Test
  @DisplayName("2 request cùng (danh mục, tiền tệ) chạy song song: đúng 1 bản được tạo (G3)")
  void parallelCreatesKeepOnePerCategoryAndCurrency() throws Exception {
    UUID food = rootExpense(ann, "Ăn uống");
    CyclicBarrier start = new CyclicBarrier(2);
    Callable<Object> attempt =
        () -> {
          start.await();
          try {
            return budget(ann, food, "VND");
          } catch (DuplicateBudgetException e) {
            return e;
          }
        };

    List<Object> results = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      List<Future<Object>> futures = pool.invokeAll(List.of(attempt, attempt));
      for (Future<Object> future : futures) {
        results.add(future.get());
      }
    }

    assertThat(results).filteredOn(BudgetView.class::isInstance).hasSize(1);
    assertThat(results).filteredOn(DuplicateBudgetException.class::isInstance).hasSize(1);
    assertGaplessSequence(ann);
  }

  @Test
  @DisplayName("DB tự chặn bản thứ 2 cùng (danh mục, tiền tệ) và tháng không phải ngày 1")
  void databaseIsTheSecondBarrier() {
    UUID food = rootExpense(ann, "Ăn uống");
    budget(ann, food, "VND");

    String insert =
        """
        insert into budgets (id, user_id, category_id, currency, amount_minor, start_month,
                             change_seq, created_at, updated_at)
        values (:id, :u, :c, 'VND', 1, :month, :seq, now(), now())
        """;
    assertThatThrownBy(
            () ->
                jdbc.sql(insert)
                    .param("id", UUID.randomUUID())
                    .param("u", ann.value())
                    .param("c", food)
                    .param("month", THIS_MONTH)
                    .param("seq", 10_000)
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.sql(insert)
                    .param("id", UUID.randomUUID())
                    .param("u", ann.value())
                    .param("c", UUID.randomUUID())
                    .param("month", THIS_MONTH.plusDays(1))
                    .param("seq", 10_001)
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // ---- consumer ledger.category.changed ----

  @Test
  @DisplayName("xoá danh mục → event → ngân sách thành tombstone với change_seq mới (GS6)")
  void deletingCategoryTombstonesItsBudgets() {
    UUID food = rootExpense(ann, "Ăn uống");
    BudgetView vnd = budget(ann, food, "VND");
    BudgetView usd = budget(ann, food, "USD");
    long before = changeSeq(vnd.id());

    deleteCategory.delete(new DeleteCategoryCommand(ann, food));

    Awaitility.await()
        .atMost(WAIT)
        .untilAsserted(() -> assertThat(isDeleted(vnd.id()) && isDeleted(usd.id())).isTrue());
    assertThat(changeSeq(vnd.id())).isGreaterThan(before);
    // 3 lần tạo + xoá danh mục + 2 tombstone ngân sách.
    assertThat(lastSeq(ann)).isEqualTo(6);
    assertConsistentSequence(ann);
  }

  @Test
  @DisplayName("danh mục thành danh mục con → ngân sách của nó thành tombstone (G14)")
  void reparentingCategoryTombstonesItsBudgets() {
    UUID food = rootExpense(ann, "Ăn uống");
    UUID drinks = rootExpense(ann, "Đồ uống");
    BudgetView onDrinks = budget(ann, drinks, "VND");
    BudgetView onFood = budget(ann, food, "VND");

    updateCategory.update(
        new UpdateCategoryCommand(
            ann, drinks, new CategoryFields("EXPENSE", "Đồ uống", food, null, null)));

    Awaitility.await().atMost(WAIT).until(() -> isDeleted(onDrinks.id()));
    assertThat(isDeleted(onFood.id())).isFalse();
  }

  @Test
  @DisplayName("event tới 2 lần, hoặc event mới cho danh mục đã xử lý: không ghi gì thêm")
  void redeliveredEventsChangeNothing() {
    UUID food = rootExpense(ann, "Ăn uống");
    BudgetView vnd = budget(ann, food, "VND");
    deleteCategory.delete(new DeleteCategoryCommand(ann, food));
    Awaitility.await().atMost(WAIT).until(() -> isDeleted(vnd.id()));
    long seq = changeSeq(vnd.id());
    long last = lastSeq(ann);

    UUID replay = UUID.randomUUID();
    publishCategoryChanged(replay, ann, food);
    publishCategoryChanged(replay, ann, food);

    Awaitility.await()
        .atMost(WAIT)
        .until(
            () ->
                jdbc.sql(
                            "select count(*) from processed_events where event_id = :e and consumer = :c")
                        .param("e", replay)
                        .param("c", QUEUE)
                        .query(Integer.class)
                        .single()
                    == 1);
    assertThat(changeSeq(vnd.id())).isEqualTo(seq);
    assertThat(lastSeq(ann)).isEqualTo(last);
  }

  @Test
  @DisplayName(
      "consumer chạy song song với các lần ghi khác của cùng user: không lỗi, không lỗ số (GS7)")
  void consumerAndWritesOfTheSameUserSerialize() throws Exception {
    int doomedCount = 8;
    List<UUID> doomed = new ArrayList<>();
    List<UUID> budgets = new ArrayList<>();
    for (int i = 0; i < doomedCount; i++) {
      UUID category = rootExpense(ann, "Xoá " + i);
      doomed.add(category);
      budgets.add(budget(ann, category, "VND").id());
    }
    long parkedBefore = parkedMessages();

    try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
      Future<?> deletes =
          pool.submit(
              () -> doomed.forEach(c -> deleteCategory.delete(new DeleteCategoryCommand(ann, c))));
      Future<?> categories =
          pool.submit(
              () -> {
                for (int i = 0; i < 15; i++) {
                  rootExpense(ann, "Mới " + i);
                }
              });
      Future<?> moreBudgets =
          pool.submit(
              () -> {
                for (int i = 0; i < 8; i++) {
                  budget(ann, rootExpense(ann, "Có ngân sách " + i), "USD");
                }
              });
      for (Future<?> future : List.of(deletes, categories, moreBudgets)) {
        try {
          future.get();
        } catch (ExecutionException e) {
          throw new AssertionError("a concurrent write failed", e.getCause());
        }
      }
    }

    Awaitility.await()
        .atMost(WAIT)
        .untilAsserted(() -> assertThat(budgets).allMatch(this::isDeleted));
    // Đếm từng lần ghi: (8 danh mục + 8 ngân sách) + (8 xoá danh mục + 8 tombstone)
    // + 15 danh mục + (8 danh mục + 8 ngân sách). Bằng last_seq nghĩa là không số nào bị phát thừa
    // hay mất do rollback/đua nhau.
    assertThat(lastSeq(ann)).isEqualTo(16 + 16 + 15 + 16);
    assertConsistentSequence(ann);
    assertThat(parkedMessages()).isEqualTo(parkedBefore);
    assertThat(
            jdbc.sql("select count(*) from budgets where user_id = :u and deleted_at is null")
                .param("u", ann.value())
                .query(Integer.class)
                .single())
        .isEqualTo(8);
  }
}

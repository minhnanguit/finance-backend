package com.uit.finance.modules.recurring.application.service;

import static com.uit.finance.modules.recurring.domain.model.RecurringFixtures.ANN;
import static com.uit.finance.modules.recurring.domain.model.RecurringFixtures.BOB;
import static com.uit.finance.modules.recurring.domain.model.RecurringFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.finance.modules.recurring.application.port.in.CreateRecurringUseCase.CreateRecurringCommand;
import com.uit.finance.modules.recurring.application.port.in.DeleteRecurringUseCase.DeleteRecurringCommand;
import com.uit.finance.modules.recurring.application.port.in.RecurringFields;
import com.uit.finance.modules.recurring.application.port.in.RecurringFields.RevisionFields;
import com.uit.finance.modules.recurring.application.port.in.RecurringView;
import com.uit.finance.modules.recurring.application.port.in.UpdateRecurringUseCase.UpdateRecurringCommand;
import com.uit.finance.modules.recurring.domain.exception.InvalidRecurringAccountException;
import com.uit.finance.modules.recurring.domain.exception.InvalidRecurringCategoryException;
import com.uit.finance.modules.recurring.domain.exception.InvalidRecurringFieldException;
import com.uit.finance.modules.recurring.domain.exception.RecurringCurrencyMismatchException;
import com.uit.finance.modules.recurring.domain.exception.RecurringLimitReachedException;
import com.uit.finance.modules.recurring.domain.exception.RecurringNotFoundException;
import com.uit.finance.modules.recurring.domain.exception.RecurringReferencePendingException;
import com.uit.finance.modules.recurring.domain.exception.RevisionConflictException;
import com.uit.finance.modules.recurring.domain.model.RecurringLimits;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecurringServicesTest {

  private RecurringFakes.Store store;
  private RecurringFakes.Ledger ledger;
  private CreateRecurringService create;
  private UpdateRecurringService update;
  private DeleteRecurringService delete;
  private UUID account;
  private UUID rent;

  @BeforeEach
  void setUp() {
    store = new RecurringFakes.Store();
    ledger = new RecurringFakes.Ledger();
    Clock clock = Clock.fixed(TODAY.atTime(8, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    create = new CreateRecurringService(store, store, store, store, ledger, clock);
    update = new UpdateRecurringService(store, store, store, ledger, clock);
    delete = new DeleteRecurringService(store, store, store, clock);
    account = ledger.account(ANN, "VND", false);
    rent = ledger.category(ANN, "EXPENSE", false);
  }

  private static RevisionFields rev(
      UUID id, LocalDate from, UUID account, UUID category, long amount, int day) {
    return new RevisionFields(
        id, from, from, account, category, amount, null, "MONTHLY", 1, null, null, day, false);
  }

  private static RecurringFields fields(long revision, List<RevisionFields> revisions) {
    return fields(revision, revisions, null);
  }

  private static RecurringFields fields(
      long revision, List<RevisionFields> revisions, LocalDate end) {
    return new RecurringFields(
        "Tiền nhà",
        "EXPENSE",
        "VND",
        revisions.getFirst().effectiveFrom(),
        end,
        2,
        revision,
        revisions);
  }

  private RevisionFields first() {
    return rev(UUID.randomUUID(), LocalDate.of(2026, 1, 1), account, rent, 3_000_000, 5);
  }

  private RecurringView createRent(UUID id, RevisionFields first) {
    return create.create(new CreateRecurringCommand(ANN, id, fields(0, List.of(first))));
  }

  @Test
  @DisplayName("tạo: khoá user trước, revision 1, bắt đầu từ quá khứ được")
  void createLocksAndStartsAtOne() {
    RecurringView view = createRent(UUID.randomUUID(), first());

    assertThat(view.revision()).isEqualTo(1);
    assertThat(store.locks).containsExactly(ANN);
  }

  @Test
  @DisplayName("tạo: ví/danh mục chưa tới hoặc của user khác → reference_pending (B2)")
  void missingReferencesArePending() {
    UUID bobsAccount = ledger.account(BOB, "VND", false);
    RevisionFields foreign = rev(UUID.randomUUID(), TODAY, bobsAccount, rent, 1, 5);

    assertThatThrownBy(
            () ->
                create.create(
                    new CreateRecurringCommand(
                        ANN, UUID.randomUUID(), fields(0, List.of(foreign)))))
        .isInstanceOf(RecurringReferencePendingException.class);
  }

  @Test
  @DisplayName("tạo: ví khác tiền tệ, ví đã xoá, danh mục khác loại → bị từ chối đúng mã")
  void unusableReferences() {
    UUID usd = ledger.account(ANN, "USD", false);
    UUID gone = ledger.account(ANN, "VND", true);
    UUID salary = ledger.category(ANN, "INCOME", false);

    assertThatThrownBy(
            () -> createRent(UUID.randomUUID(), rev(UUID.randomUUID(), TODAY, usd, rent, 1, 5)))
        .isInstanceOf(RecurringCurrencyMismatchException.class);
    assertThatThrownBy(
            () -> createRent(UUID.randomUUID(), rev(UUID.randomUUID(), TODAY, gone, rent, 1, 5)))
        .isInstanceOf(InvalidRecurringAccountException.class);
    assertThatThrownBy(
            () ->
                createRent(UUID.randomUUID(), rev(UUID.randomUUID(), TODAY, account, salary, 1, 5)))
        .isInstanceOf(InvalidRecurringCategoryException.class);
  }

  @Test
  @DisplayName("tạo: id của user khác → not_found, bản của người kia không đổi")
  void foreignIdIsNotFound() {
    UUID id = UUID.randomUUID();
    UUID bobsAccount = ledger.account(BOB, "VND", false);
    UUID bobsCategory = ledger.category(BOB, "EXPENSE", false);
    create.create(
        new CreateRecurringCommand(
            BOB,
            id,
            fields(0, List.of(rev(UUID.randomUUID(), TODAY, bobsAccount, bobsCategory, 9, 5)))));

    assertThatThrownBy(() -> createRent(id, first()))
        .isInstanceOf(RecurringNotFoundException.class);
    assertThat(store.rows.get(id).isOwnedBy(BOB)).isTrue();
  }

  @Test
  @DisplayName("tạo khi đã có 200 khoản còn hiệu lực → limit_reached; khoản đã xoá không tính")
  void limitCountsOnlyActive() {
    for (int i = 0; i < RecurringLimits.MAX_ACTIVE_PER_USER; i++) {
      createRent(UUID.randomUUID(), first());
    }
    assertThatThrownBy(() -> createRent(UUID.randomUUID(), first()))
        .isInstanceOf(RecurringLimitReachedException.class);

    UUID id = store.rows.keySet().iterator().next();
    delete.delete(new DeleteRecurringCommand(ANN, id));

    assertThat(createRent(UUID.randomUUID(), first()).revision()).isEqualTo(1);
  }

  @Test
  @DisplayName("tạo khi đã có 2.000 khoản chưa xoá (kể cả đã kết thúc) → limit_reached")
  void limitCountsStoredRecurrings() {
    LocalDate ended = TODAY.minusDays(10);
    for (int i = 0; i < RecurringLimits.MAX_STORED_PER_USER; i++) {
      create.create(
          new CreateRecurringCommand(ANN, UUID.randomUUID(), fields(0, List.of(first()), ended)));
    }

    assertThatThrownBy(() -> createRent(UUID.randomUUID(), first()))
        .isInstanceOf(RecurringLimitReachedException.class);
  }

  @Test
  @DisplayName("sửa với revision cũ → revision_conflict; với revision đúng → tăng 1")
  void updateIsConditional() {
    UUID id = UUID.randomUUID();
    RecurringView created = createRent(id, first());
    List<RevisionFields> timeline = new ArrayList<>(toFields(created));
    timeline.add(rev(UUID.randomUUID(), TODAY.plusDays(21), account, rent, 3_200_000, 5));

    RecurringView updated = update.update(new UpdateRecurringCommand(ANN, id, fields(1, timeline)));

    assertThat(updated.revision()).isEqualTo(2);
    assertThatThrownBy(
            () -> update.update(new UpdateRecurringCommand(ANN, id, fields(1, toFields(created)))))
        .isInstanceOf(RevisionConflictException.class);
  }

  @Test
  @DisplayName("sửa: chỉ phiên bản mới bị kiểm tham chiếu; phiên bản cũ trỏ ví đã xoá vẫn qua")
  void onlyChangedRevisionsAreChecked() {
    UUID id = UUID.randomUUID();
    RecurringView created = createRent(id, first());
    ledger
        .accounts
        .get(ANN)
        .put(
            account,
            new com.uit.finance.modules.ledger.application.port.in.reference.AccountRef(
                account, "VND", false, true));
    UUID other = ledger.account(ANN, "VND", false);
    List<RevisionFields> timeline = new ArrayList<>(toFields(created));
    timeline.add(rev(UUID.randomUUID(), TODAY.plusDays(1), other, rent, 1, 5));

    assertThat(update.update(new UpdateRecurringCommand(ANN, id, fields(1, timeline))).revision())
        .isEqualTo(2);
  }

  @Test
  @DisplayName(
      "tạo lại id đã có với revision 0 → revision_conflict; revision khác 0 khi tạo → invalid")
  void createRules() {
    UUID id = UUID.randomUUID();
    createRent(id, first());

    assertThatThrownBy(() -> createRent(id, first())).isInstanceOf(RevisionConflictException.class);
    assertThatThrownBy(
            () ->
                create.create(
                    new CreateRecurringCommand(
                        ANN, UUID.randomUUID(), fields(4, List.of(first())))))
        .isInstanceOf(InvalidRecurringFieldException.class);
  }

  @Test
  @DisplayName("xoá: tombstone, xoá lại là no-op, id của người khác → not_found")
  void deleteIsSoft() {
    UUID id = UUID.randomUUID();
    createRent(id, first());

    assertThat(delete.delete(new DeleteRecurringCommand(ANN, id)).deleted()).isTrue();
    assertThat(delete.delete(new DeleteRecurringCommand(ANN, id)).deleted()).isTrue();
    assertThatThrownBy(() -> delete.delete(new DeleteRecurringCommand(BOB, id)))
        .isInstanceOf(RecurringNotFoundException.class);
  }

  private static List<RevisionFields> toFields(RecurringView view) {
    return view.terms().revisions().stream()
        .map(
            r ->
                new RevisionFields(
                    r.id(),
                    r.effectiveFrom(),
                    r.anchorDate(),
                    r.accountId(),
                    r.categoryId(),
                    r.amountMinor(),
                    r.note(),
                    r.rule().frequency().name(),
                    r.rule().interval(),
                    r.rule().weekday(),
                    r.rule().month(),
                    r.rule().dayOfMonth(),
                    r.autoConfirm()))
        .toList();
  }
}

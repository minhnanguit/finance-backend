package com.uit.finance.modules.recurring.domain.model;

import static com.uit.finance.modules.recurring.domain.model.RecurringFixtures.ANN;
import static com.uit.finance.modules.recurring.domain.model.RecurringFixtures.TODAY;
import static com.uit.finance.modules.recurring.domain.model.RecurringFixtures.monthly;
import static com.uit.finance.modules.recurring.domain.model.RecurringFixtures.revision;
import static com.uit.finance.modules.recurring.domain.model.RecurringFixtures.terms;
import static com.uit.finance.modules.recurring.domain.model.RecurringFixtures.withEnd;
import static com.uit.finance.modules.recurring.domain.model.RecurringFixtures.withNotBefore;
import static com.uit.finance.modules.recurring.domain.model.RecurringFixtures.withRevision;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uit.finance.modules.recurring.domain.exception.ImmutableRecurringFieldException;
import com.uit.finance.modules.recurring.domain.exception.InvalidRecurringFieldException;
import com.uit.finance.modules.recurring.domain.exception.RecurringDeletedException;
import com.uit.finance.modules.recurring.domain.exception.RevisionConflictException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecurringTest {

  private final UUID account = UUID.randomUUID();
  private final UUID category = UUID.randomUUID();
  private final RecurrenceRevision first =
      revision(LocalDate.of(2026, 1, 1), account, category, 3_000_000, monthly(5));

  private Recurring created() {
    return Recurring.create(
        RecurringId.of(UUID.randomUUID()), ANN, terms(List.of(first)), 0, TODAY);
  }

  @Test
  @DisplayName(
      "tạo mới: revision 1, được phép bắt đầu trong quá khứ; revision gửi lên khác 0 bị từ chối")
  void createStartsAtRevisionOne() {
    assertThat(created().getRevision()).isEqualTo(1);
    assertThatThrownBy(
            () ->
                Recurring.create(
                    RecurringId.of(UUID.randomUUID()), ANN, terms(List.of(first)), 3, TODAY))
        .isInstanceOf(InvalidRecurringFieldException.class);
  }

  @Test
  @DisplayName("đổi giá từ tháng sau: revision tăng đúng 1, phiên bản cũ giữ nguyên")
  void addingAFutureRevisionBumpsRevision() {
    Recurring recurring = created();
    RecurrenceRevision raise =
        new RecurrenceRevision(
            UUID.randomUUID(),
            LocalDate.of(2026, 12, 1),
            first.anchorDate(),
            account,
            category,
            3_200_000,
            null,
            monthly(5),
            false);

    boolean changed = recurring.revise(withRevision(recurring.getTerms(), raise), 1, TODAY);

    assertThat(changed).isTrue();
    assertThat(recurring.getRevision()).isEqualTo(2);
    assertThat(recurring.getTerms().revisions()).containsExactly(first, raise);
  }

  @Test
  @DisplayName("gửi lại y nguyên: không đổi, revision giữ nguyên")
  void identicalTermsAreANoOp() {
    Recurring recurring = created();

    assertThat(recurring.revise(recurring.getTerms(), 1, TODAY)).isFalse();
    assertThat(recurring.getRevision()).isEqualTo(1);
  }

  @Test
  @DisplayName("revision cũ: recurring.revision_conflict, không đổi gì")
  void staleRevisionConflicts() {
    Recurring recurring = created();
    recurring.revise(withEnd(recurring.getTerms(), TODAY.plusDays(30)), 1, TODAY);

    assertThatThrownBy(
            () -> recurring.revise(withEnd(recurring.getTerms(), TODAY.plusDays(10)), 1, TODAY))
        .isInstanceOf(RevisionConflictException.class);
    assertThat(recurring.getTerms().generationEndDate()).isEqualTo(TODAY.plusDays(30));
  }

  @Test
  @DisplayName("sửa phiên bản đã hiệu lực hoặc thêm phiên bản vào quá khứ: immutable_field")
  void pastRevisionsAreImmutable() {
    Recurring recurring = created();
    RecurrenceRevision edited =
        new RecurrenceRevision(
            first.id(),
            first.effectiveFrom(),
            first.anchorDate(),
            account,
            category,
            1,
            null,
            monthly(5),
            false);
    RecurrenceRevision backdated = revision(TODAY.minusDays(5), account, category, 10, monthly(5));

    assertThatThrownBy(() -> recurring.revise(terms(List.of(edited)), 1, TODAY))
        .isInstanceOf(ImmutableRecurringFieldException.class);
    assertThatThrownBy(
            () -> recurring.revise(withRevision(recurring.getTerms(), backdated), 1, TODAY))
        .isInstanceOf(ImmutableRecurringFieldException.class);
  }

  @Test
  @DisplayName("phiên bản mới từ hôm qua (UTC) vẫn được nhận: dung sai múi giờ")
  void yesterdayIsWithinTolerance() {
    Recurring recurring = created();
    RecurrenceRevision fromYesterday =
        revision(TODAY.minusDays(1), account, category, 10, monthly(10));

    assertThat(recurring.revise(withRevision(recurring.getTerms(), fromYesterday), 1, TODAY))
        .isTrue();
  }

  @Test
  @DisplayName("phiên bản quá 366 ngày tới: invalid_field")
  void tooFarAheadIsInvalid() {
    Recurring recurring = created();
    RecurrenceRevision far = revision(TODAY.plusDays(367), account, category, 10, monthly(5));

    assertThatThrownBy(() -> recurring.revise(withRevision(recurring.getTerms(), far), 1, TODAY))
        .isInstanceOf(InvalidRecurringFieldException.class);
  }

  @Test
  @DisplayName("kết thúc chỉ rút ngắn: gỡ hoặc kéo dài bị chặn, đặt trước hôm qua bị chặn")
  void endOnlyShrinks() {
    Recurring recurring = created();
    recurring.revise(withEnd(recurring.getTerms(), TODAY.plusDays(30)), 1, TODAY);

    assertThatThrownBy(
            () -> recurring.revise(withEnd(recurring.getTerms(), TODAY.plusDays(60)), 2, TODAY))
        .isInstanceOf(ImmutableRecurringFieldException.class);
    assertThatThrownBy(
            () -> recurring.revise(withEnd(recurring.getTerms(), TODAY.minusDays(2)), 2, TODAY))
        .isInstanceOf(InvalidRecurringFieldException.class);
    RecurringTerms reopened =
        new RecurringTerms(
            "Tiền nhà",
            RecurringType.EXPENSE,
            RecurringFixtures.VND,
            recurring.getTerms().generationNotBefore(),
            null,
            2,
            recurring.getTerms().revisions());
    assertThatThrownBy(() -> recurring.revise(reopened, 2, TODAY))
        .isInstanceOf(ImmutableRecurringFieldException.class);
  }

  @Test
  @DisplayName("huỷ khoản chưa bắt đầu: ngày kết thúc trước kỳ đầu được nhận")
  void cancelBeforeFirstOccurrence() {
    RecurrenceRevision future = revision(TODAY.plusDays(20), account, category, 10, monthly(5));
    Recurring recurring =
        Recurring.create(RecurringId.of(UUID.randomUUID()), ANN, terms(List.of(future)), 0, TODAY);

    assertThat(recurring.revise(withEnd(recurring.getTerms(), TODAY.minusDays(1)), 1, TODAY))
        .isTrue();
    assertThat(recurring.isActiveOn(TODAY.plusDays(1))).isFalse();
  }

  @Test
  @DisplayName("mốc không sinh trước chỉ tăng, tối đa ngày mai")
  void notBeforeOnlyGrows() {
    Recurring recurring = created();

    assertThatThrownBy(
            () ->
                recurring.revise(
                    withNotBefore(recurring.getTerms(), LocalDate.of(2025, 1, 1)), 1, TODAY))
        .isInstanceOf(ImmutableRecurringFieldException.class);
    assertThatThrownBy(
            () ->
                recurring.revise(withNotBefore(recurring.getTerms(), TODAY.plusDays(2)), 1, TODAY))
        .isInstanceOf(InvalidRecurringFieldException.class);
    assertThat(recurring.revise(withNotBefore(recurring.getTerms(), TODAY), 1, TODAY)).isTrue();
  }

  @Test
  @DisplayName("đổi type hoặc currency: immutable_field")
  void typeAndCurrencyAreImmutable() {
    Recurring recurring = created();
    RecurringTerms income =
        new RecurringTerms(
            "Lương",
            RecurringType.INCOME,
            RecurringFixtures.VND,
            recurring.getTerms().generationNotBefore(),
            null,
            null,
            recurring.getTerms().revisions());

    assertThatThrownBy(() -> recurring.revise(income, 1, TODAY))
        .isInstanceOf(ImmutableRecurringFieldException.class);
  }

  @Test
  @DisplayName("chỉ phiên bản thêm hoặc sửa phải kiểm tham chiếu")
  void changedRevisionsAreOnlyTheNewOnes() {
    Recurring recurring = created();
    RecurrenceRevision raise = revision(TODAY.plusDays(5), account, category, 10, monthly(5));

    assertThat(recurring.changedRevisions(withRevision(recurring.getTerms(), raise)))
        .containsExactly(raise);
  }

  @Test
  @DisplayName("đã xoá: sửa là recurring.deleted; xoá lại là no-op")
  void deletedWins() {
    Recurring recurring = created();
    assertThat(recurring.delete(Instant.EPOCH)).isTrue();
    assertThat(recurring.delete(Instant.EPOCH)).isFalse();

    assertThatThrownBy(() -> recurring.revise(recurring.getTerms(), 1, TODAY))
        .isInstanceOf(RecurringDeletedException.class);
  }

  @Test
  @DisplayName(
      "hình dạng: tổ hợp lịch sai, effectiveFrom không tăng, quá 50 phiên bản, anchor sau effectiveFrom")
  void shapeRules() {
    assertThatThrownBy(() -> new RecurrenceRule(Frequency.MONTHLY, 1, 1, null, 5))
        .isInstanceOf(InvalidRecurringFieldException.class);
    assertThatThrownBy(() -> new RecurrenceRule(Frequency.WEEKLY, 1, null, null, null))
        .isInstanceOf(InvalidRecurringFieldException.class);
    assertThatThrownBy(() -> new RecurrenceRule(Frequency.YEARLY, 13, null, 2, 29))
        .isInstanceOf(InvalidRecurringFieldException.class);
    RecurrenceRevision same = revision(first.effectiveFrom(), account, category, 1, monthly(1));
    assertThatThrownBy(() -> terms(List.of(first, same)))
        .isInstanceOf(InvalidRecurringFieldException.class);
    List<RecurrenceRevision> many = new ArrayList<>();
    for (int i = 0; i < 51; i++) {
      many.add(revision(LocalDate.of(2026, 1, 1).plusDays(i), account, category, 1, monthly(1)));
    }
    assertThatThrownBy(() -> terms(many)).isInstanceOf(InvalidRecurringFieldException.class);
    assertThatThrownBy(
            () ->
                new RecurrenceRevision(
                    UUID.randomUUID(),
                    TODAY,
                    TODAY.plusDays(1),
                    account,
                    category,
                    1,
                    null,
                    monthly(1),
                    false))
        .isInstanceOf(InvalidRecurringFieldException.class);
  }

  @Test
  @DisplayName("UUIDv5 khớp vector RFC và vector chung của ADR-008 §2")
  void occurrenceIdsMatchTheSharedVectors() {
    UUID dns = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
    RecurringId rid = RecurringId.of(UUID.fromString("3f1c2b9e-7a4d-4e8b-9c21-5d6e7f8a9b0c"));

    assertThat(OccurrenceIds.uuidV5(dns, "www.example.com"))
        .hasToString("2ed6657d-e927-568b-95e1-2665a8aea6a2");
    assertThat(OccurrenceIds.of(rid, LocalDate.of(2026, 12, 5)))
        .hasToString("0cdf7899-a9f2-53ca-b1c4-f9475ebfe6a0");
    assertThat(OccurrenceIds.of(rid, LocalDate.of(2027, 2, 28)))
        .hasToString("b62a0a6d-780d-538f-9512-a0d2e7fc9cea");
    assertThat(OccurrenceIds.of(rid, LocalDate.of(2028, 2, 29)))
        .hasToString("bee84904-e343-5093-9109-a21e2e728584");
  }
}

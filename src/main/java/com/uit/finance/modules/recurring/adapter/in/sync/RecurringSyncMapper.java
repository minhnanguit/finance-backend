package com.uit.finance.modules.recurring.adapter.in.sync;

import com.uit.finance.modules.recurring.application.port.in.RecurringChangeFeedUseCase.RecurringChange;
import com.uit.finance.modules.recurring.application.port.in.RecurringFields;
import com.uit.finance.modules.recurring.application.port.in.RecurringFields.RevisionFields;
import com.uit.finance.modules.recurring.application.port.in.RecurringView;
import com.uit.finance.modules.recurring.domain.exception.InvalidRecurringFieldException;
import com.uit.finance.modules.recurring.domain.model.RecurrenceRevision;
import com.uit.finance.modules.recurring.domain.model.RecurringTerms;
import com.uit.finance.shared.sync.SyncChange;
import com.uit.finance.shared.sync.SyncData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * {@code data} của op ↔ khoản định kỳ, theo schema {@code RecurringData} + {@code
 * RecurrenceRevisionData} của contract 2.4.0.
 */
final class RecurringSyncMapper {

  static final String ENTITY = "recurring";

  /** Mọi field đi cả 2 chiều; field do server quản lý khác gửi lên là field lạ (B3). */
  static final Set<String> FIELDS =
      Set.of(
          "name",
          "type",
          "currency",
          "generationNotBefore",
          "generationEndDate",
          "remindDaysBefore",
          "revision",
          "revisions");

  static final Set<String> REVISION_FIELDS =
      Set.of(
          "id",
          "effectiveFrom",
          "anchorDate",
          "accountId",
          "categoryId",
          "amountMinor",
          "note",
          "frequency",
          "interval",
          "weekday",
          "month",
          "dayOfMonth",
          "autoConfirm");

  private RecurringSyncMapper() {}

  static RecurringFields read(@Nullable Map<String, Object> raw) {
    SyncData data = SyncData.read(raw, FIELDS);
    List<Map<String, Object>> revisions = data.objects("revisions");
    if (revisions == null) {
      throw new InvalidRecurringFieldException("revisions");
    }
    return new RecurringFields(
        data.text("name"),
        data.text("type"),
        data.text("currency"),
        data.date("generationNotBefore"),
        data.date("generationEndDate"),
        data.optionalInt("remindDaysBefore"),
        data.requiredLong("revision"),
        revisions.stream().map(RecurringSyncMapper::readRevision).toList());
  }

  private static RevisionFields readRevision(Map<String, Object> raw) {
    SyncData data = SyncData.read(raw, REVISION_FIELDS);
    return new RevisionFields(
        data.uuid("id"),
        data.date("effectiveFrom"),
        data.date("anchorDate"),
        data.uuid("accountId"),
        data.uuid("categoryId"),
        data.requiredLong("amountMinor"),
        data.text("note"),
        data.text("frequency"),
        data.requiredInt("interval"),
        data.optionalInt("weekday"),
        data.optionalInt("month"),
        data.optionalInt("dayOfMonth"),
        data.requiredBoolean("autoConfirm"));
  }

  static SyncChange toChange(RecurringChange change) {
    RecurringView view = change.recurring();
    return new SyncChange(ENTITY, view.id(), change.changeSeq(), view.deleted(), toData(view));
  }

  private static Map<String, Object> toData(RecurringView view) {
    RecurringTerms terms = view.terms();
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", terms.name());
    data.put("type", terms.type().name());
    data.put("currency", terms.currency().getCurrencyCode());
    data.put("generationNotBefore", terms.generationNotBefore().toString());
    data.put(
        "generationEndDate",
        terms.generationEndDate() == null ? null : terms.generationEndDate().toString());
    data.put("remindDaysBefore", terms.remindDaysBefore());
    data.put("revision", view.revision());
    data.put("revisions", terms.revisions().stream().map(RecurringSyncMapper::toData).toList());
    return data;
  }

  private static Map<String, Object> toData(RecurrenceRevision r) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", r.id().toString());
    data.put("effectiveFrom", r.effectiveFrom().toString());
    data.put("anchorDate", r.anchorDate().toString());
    data.put("accountId", r.accountId().toString());
    data.put("categoryId", r.categoryId().toString());
    data.put("amountMinor", r.amountMinor());
    data.put("note", r.note());
    data.put("frequency", r.rule().frequency().name());
    data.put("interval", r.rule().interval());
    data.put("weekday", r.rule().weekday());
    data.put("month", r.rule().month());
    data.put("dayOfMonth", r.rule().dayOfMonth());
    data.put("autoConfirm", r.autoConfirm());
    return data;
  }
}

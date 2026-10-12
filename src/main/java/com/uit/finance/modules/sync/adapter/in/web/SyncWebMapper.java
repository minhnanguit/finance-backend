package com.uit.finance.modules.sync.adapter.in.web;

import com.uit.finance.api.v1.model.SyncChange;
import com.uit.finance.api.v1.model.SyncOpResult;
import com.uit.finance.api.v1.model.SyncOperation;
import com.uit.finance.api.v1.model.SyncPullResponse;
import com.uit.finance.api.v1.model.SyncPushResponse;
import com.uit.finance.modules.sync.domain.model.ChangePage;
import com.uit.finance.modules.sync.domain.model.ChangeRecord;
import com.uit.finance.modules.sync.domain.model.OpAction;
import com.uit.finance.modules.sync.domain.model.OpResult;
import com.uit.finance.modules.sync.domain.model.PushedOp;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class SyncWebMapper {

  PushedOp toPushedOp(SyncOperation op) {
    return new PushedOp(
        op.getOpId(),
        op.getEntity(),
        op.getId(),
        OpAction.valueOf(op.getAction().getValue()),
        op.getAction() == SyncOperation.ActionEnum.DELETE ? null : op.getData());
  }

  SyncPushResponse toPushResponse(List<OpResult> results) {
    return new SyncPushResponse().results(results.stream().map(this::toOpResult).toList());
  }

  SyncPullResponse toPullResponse(ChangePage page) {
    return new SyncPullResponse()
        .changes(page.changes().stream().map(this::toChange).toList())
        .nextCursor(page.next().encode())
        .hasMore(page.hasMore());
  }

  private SyncOpResult toOpResult(OpResult result) {
    return new SyncOpResult()
        .opId(result.opId())
        .outcome(SyncOpResult.OutcomeEnum.fromValue(result.outcome().name()))
        .code(result.code())
        .current(result.current() == null ? null : toChange(result.current()));
  }

  private SyncChange toChange(ChangeRecord record) {
    return new SyncChange()
        .entity(record.entity())
        .id(record.id())
        .changeSeq(record.changeSeq())
        .deleted(record.deleted())
        .data(record.data());
  }
}

package com.uit.finance.modules.sync.adapter.in.web;

import com.uit.finance.api.v1.SyncApi;
import com.uit.finance.api.v1.model.SyncPullResponse;
import com.uit.finance.api.v1.model.SyncPushRequest;
import com.uit.finance.api.v1.model.SyncPushResponse;
import com.uit.finance.modules.sync.application.port.in.PullChangesUseCase;
import com.uit.finance.modules.sync.application.port.in.PullChangesUseCase.PullQuery;
import com.uit.finance.modules.sync.application.port.in.PushChangesUseCase;
import com.uit.finance.modules.sync.application.port.in.PushChangesUseCase.PushCommand;
import com.uit.finance.shared.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code userId} chỉ lấy từ token, không bao giờ từ body (ADR-006 B3). Giới hạn tần suất và kích
 * thước body đã được filter trong {@code shared.web} chặn trước khi tới đây.
 */
@RestController
class SyncController implements SyncApi {

  private final PushChangesUseCase push;
  private final PullChangesUseCase pull;
  private final SyncWebMapper mapper;

  SyncController(PushChangesUseCase push, PullChangesUseCase pull, SyncWebMapper mapper) {
    this.push = push;
    this.pull = pull;
    this.mapper = mapper;
  }

  @Override
  public ResponseEntity<SyncPushResponse> pushChanges(
      UUID idempotencyKey, SyncPushRequest request) {
    PushCommand command =
        new PushCommand(
            AuthenticatedUser.requireCurrent(),
            request.getDeviceId(),
            request.getOps().stream().map(mapper::toPushedOp).toList());
    return ResponseEntity.ok(mapper.toPushResponse(push.push(command)));
  }

  @Override
  public ResponseEntity<SyncPullResponse> pullChanges(String since, Integer limit) {
    return ResponseEntity.ok(
        mapper.toPullResponse(
            pull.pull(new PullQuery(AuthenticatedUser.requireCurrent(), since, limit))));
  }
}

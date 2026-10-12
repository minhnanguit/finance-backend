package com.uit.finance.modules.sync.application.port.in;

import com.uit.finance.modules.sync.domain.model.ChangePage;
import com.uit.finance.shared.kernel.UserId;
import org.jspecify.annotations.Nullable;

/** Trả một trang thay đổi của chính user kể từ cursor (ADR-002 §4). */
public interface PullChangesUseCase {

  ChangePage pull(PullQuery query);

  record PullQuery(UserId userId, @Nullable String cursor, int limit) {}
}

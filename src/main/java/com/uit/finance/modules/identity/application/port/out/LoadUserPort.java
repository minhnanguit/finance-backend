package com.uit.finance.modules.identity.application.port.out;

import com.uit.finance.modules.identity.domain.model.ExternalSubject;
import com.uit.finance.modules.identity.domain.model.User;
import com.uit.finance.shared.kernel.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LoadUserPort {

  Optional<User> byId(UserId id);

  Optional<User> byExternalSubject(ExternalSubject subject);

  /**
   * Tối đa {@code limit} tài khoản {@code DELETING} yêu cầu xoá trước {@code requestedBefore}, khoá
   * tới hết transaction. Dòng đang bị instance khác khoá thì bỏ qua ({@code SKIP LOCKED}), nên
   * nhiều instance chạy job cùng lúc không xử lý trùng.
   */
  List<User> lockPendingDeletions(Instant requestedBefore, int limit);
}

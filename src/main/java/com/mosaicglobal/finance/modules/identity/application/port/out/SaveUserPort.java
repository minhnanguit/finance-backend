package com.mosaicglobal.finance.modules.identity.application.port.out;

import com.mosaicglobal.finance.modules.identity.domain.model.User;

public interface SaveUserPort {

  /**
   * Inserts the account unless another row already claims the same external subject.
   *
   * <p>Returns {@code false} instead of throwing on that conflict: two concurrent first-ever
   * requests for one subject are normal, and an exception here would doom the surrounding
   * transaction and turn a race into a failed request.
   *
   * @return {@code true} when this call created the row
   */
  boolean insertIfAbsent(User user);

  /** Persists a profile change on an account that already exists. */
  void update(User user);
}

package com.mosaicglobal.finance.modules.identity.application.port.out;

import com.mosaicglobal.finance.modules.identity.domain.model.User;

public interface SaveUserPort {

  /**
   * Trả {@code false} thay vì throw exception khi {@code sub} đã tồn tại: hai concurrent request
   * đầu tiên là chuyện bình thường, còn exception sẽ đánh dấu transaction bên ngoài là
   * rollback-only và biến race thành request lỗi.
   *
   * @return {@code true} nếu lần gọi này insert được row
   */
  boolean insertIfAbsent(User user);

  void update(User user);
}

package com.mosaicglobal.finance.modules.identity.application.port.out;

import com.mosaicglobal.finance.modules.identity.domain.model.ExternalSubject;
import com.mosaicglobal.finance.modules.identity.domain.model.User;
import com.mosaicglobal.finance.shared.kernel.UserId;
import java.util.Optional;

public interface LoadUserPort {

  Optional<User> byId(UserId id);

  /** Lookup on the authentication path. Backed by a unique index. */
  Optional<User> byExternalSubject(ExternalSubject subject);
}

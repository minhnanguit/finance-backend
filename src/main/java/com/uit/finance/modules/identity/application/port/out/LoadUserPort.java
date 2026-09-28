package com.uit.finance.modules.identity.application.port.out;

import com.uit.finance.modules.identity.domain.model.ExternalSubject;
import com.uit.finance.modules.identity.domain.model.User;
import com.uit.finance.shared.kernel.UserId;
import java.util.Optional;

public interface LoadUserPort {

  Optional<User> byId(UserId id);

  Optional<User> byExternalSubject(ExternalSubject subject);
}

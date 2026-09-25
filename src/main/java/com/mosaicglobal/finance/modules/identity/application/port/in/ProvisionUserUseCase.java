package com.mosaicglobal.finance.modules.identity.application.port.in;

import com.mosaicglobal.finance.shared.kernel.UserId;

/**
 * Ensures a local account exists for an authenticated identity-provider subject, and returns the id
 * the rest of the system uses.
 *
 * <p>Runs on the authentication path of every request whose subject is not cached, so it must stay
 * cheap and must be safe to call concurrently for the same subject.
 */
public interface ProvisionUserUseCase {

  UserId provision(ProvisionUserCommand command);

  /**
   * @param externalSubject the {@code sub} claim — the only stable key
   * @param email current e-mail at the provider; may change between calls
   * @param displayName current display name at the provider; may change between calls
   */
  record ProvisionUserCommand(String externalSubject, String email, String displayName) {}
}

package com.mosaicglobal.finance.modules.identity.application.service;

import com.mosaicglobal.finance.modules.identity.application.port.in.DeviceDescriptor;
import com.mosaicglobal.finance.modules.identity.application.port.out.AccessTokenIssuerPort;
import com.mosaicglobal.finance.modules.identity.application.port.out.LoadRefreshTokenPort;
import com.mosaicglobal.finance.modules.identity.application.port.out.LoadUserPort;
import com.mosaicglobal.finance.modules.identity.application.port.out.PasswordHasherPort;
import com.mosaicglobal.finance.modules.identity.application.port.out.RefreshTokenGeneratorPort;
import com.mosaicglobal.finance.modules.identity.application.port.out.RevokeDeviceSessionsPort;
import com.mosaicglobal.finance.modules.identity.application.port.out.SaveRefreshTokenPort;
import com.mosaicglobal.finance.modules.identity.application.port.out.SaveUserPort;
import com.mosaicglobal.finance.modules.identity.domain.model.DeviceId;
import com.mosaicglobal.finance.modules.identity.domain.model.DevicePlatform;
import com.mosaicglobal.finance.modules.identity.domain.model.Email;
import com.mosaicglobal.finance.modules.identity.domain.model.PasswordHash;
import com.mosaicglobal.finance.modules.identity.domain.model.RawPassword;
import com.mosaicglobal.finance.modules.identity.domain.model.RefreshToken;
import com.mosaicglobal.finance.modules.identity.domain.model.RefreshTokenId;
import com.mosaicglobal.finance.modules.identity.domain.model.TokenHash;
import com.mosaicglobal.finance.modules.identity.domain.model.User;
import com.mosaicglobal.finance.shared.kernel.DomainEvent;
import com.mosaicglobal.finance.shared.kernel.DomainEventPublisher;
import com.mosaicglobal.finance.shared.kernel.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hand-written fakes for the outbound ports. Application services are tested against these, which
 * is only possible because the layer has no framework dependency.
 */
final class IdentityFakes {

  static final Instant NOW = Instant.parse("2026-09-12T10:00:00Z");
  static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  static final SessionSettings SETTINGS = new SessionSettings(Duration.ofDays(30));
  static final DeviceDescriptor DEVICE =
      new DeviceDescriptor("device-0001", "Pixel 9", DevicePlatform.ANDROID);

  private IdentityFakes() {}

  static final class InMemoryUsers implements LoadUserPort, SaveUserPort {
    final Map<UserId, User> byId = new LinkedHashMap<>();

    @Override
    public Optional<User> byId(UserId id) {
      return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<User> byEmail(Email email) {
      return byId.values().stream().filter(u -> u.getEmail().equals(email)).findFirst();
    }

    @Override
    public void save(User user) {
      byId.put(user.getId(), user);
    }
  }

  static final class InMemoryRefreshTokens
      implements LoadRefreshTokenPort, SaveRefreshTokenPort, RevokeDeviceSessionsPort {
    final Map<RefreshTokenId, RefreshToken> byId = new LinkedHashMap<>();

    @Override
    public Optional<RefreshToken> byHash(TokenHash hash) {
      return byId.values().stream().filter(t -> t.getTokenHash().equals(hash)).findFirst();
    }

    @Override
    public void save(RefreshToken token) {
      byId.put(token.getId(), token);
    }

    @Override
    public int revokeAllForDevice(UserId userId, DeviceId deviceId, Instant now) {
      List<RefreshToken> hits =
          byId.values().stream()
              .filter(t -> t.belongsTo(userId) && t.isBoundTo(deviceId) && !t.isRevoked())
              .toList();
      hits.forEach(t -> t.revoke(now));
      return hits.size();
    }
  }

  /** Reversible "hash" so tests can read what was stored. Never do this in production. */
  static final class FakePasswordHasher implements PasswordHasherPort {
    final AtomicInteger matchCalls = new AtomicInteger();

    @Override
    public PasswordHash hash(RawPassword password) {
      return new PasswordHash("hashed:" + password.value());
    }

    @Override
    public boolean matches(RawPassword password, PasswordHash hash) {
      matchCalls.incrementAndGet();
      return hash.value().equals("hashed:" + password.value());
    }

    @Override
    public PasswordHash placeholderHash() {
      return new PasswordHash("hashed:placeholder");
    }
  }

  static final class SequentialRefreshTokenGenerator implements RefreshTokenGeneratorPort {
    private int counter;

    @Override
    public GeneratedRefreshToken generate() {
      String raw = "raw-" + (++counter);
      return new GeneratedRefreshToken(raw, hash(raw));
    }

    @Override
    public TokenHash hash(String rawToken) {
      return new TokenHash("sha:" + rawToken);
    }
  }

  static final class FakeAccessTokenIssuer implements AccessTokenIssuerPort {
    @Override
    public IssuedAccessToken issue(UserId userId, DeviceId deviceId) {
      return new IssuedAccessToken("jwt:" + userId + ":" + deviceId, Duration.ofMinutes(15));
    }
  }

  static final class RecordingEventPublisher implements DomainEventPublisher {
    final List<DomainEvent> published = new ArrayList<>();

    @Override
    public void publish(DomainEvent event) {
      published.add(event);
    }
  }

  static SessionIssuer sessionIssuer(
      SequentialRefreshTokenGenerator generator, InMemoryRefreshTokens tokens) {
    return new SessionIssuer(generator, new FakeAccessTokenIssuer(), tokens, SETTINGS);
  }
}

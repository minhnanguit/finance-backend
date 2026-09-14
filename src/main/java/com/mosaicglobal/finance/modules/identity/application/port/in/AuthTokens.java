package com.mosaicglobal.finance.modules.identity.application.port.in;

import java.time.Duration;

/** Result of opening or refreshing a session. */
public record AuthTokens(String accessToken, String refreshToken, Duration accessTokenTtl) {}

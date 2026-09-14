package com.mosaicglobal.finance.modules.identity.application.service;

import java.time.Duration;

/** Tunables the application layer needs; bound from configuration by the adapter layer. */
public record SessionSettings(Duration refreshTokenTtl) {}

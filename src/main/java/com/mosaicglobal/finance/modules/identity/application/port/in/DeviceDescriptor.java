package com.mosaicglobal.finance.modules.identity.application.port.in;

import com.mosaicglobal.finance.modules.identity.domain.model.DevicePlatform;

/** Device data as it arrives from the outside; validated when turned into a {@code Device}. */
public record DeviceDescriptor(String deviceId, String deviceName, DevicePlatform platform) {}

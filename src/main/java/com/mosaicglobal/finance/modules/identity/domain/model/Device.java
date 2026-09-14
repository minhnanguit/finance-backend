package com.mosaicglobal.finance.modules.identity.domain.model;

import com.mosaicglobal.finance.shared.kernel.Ensure;

/** The installation a session is bound to. */
public record Device(DeviceId id, String name, DevicePlatform platform) {

  public Device {
    Ensure.notNull(id, "device.id");
    Ensure.lengthBetween(name, 1, 100, "device.name");
    Ensure.notNull(platform, "device.platform");
  }
}

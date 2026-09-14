package com.mosaicglobal.finance.modules.identity.application.service;

import com.mosaicglobal.finance.modules.identity.application.port.in.DeviceDescriptor;
import com.mosaicglobal.finance.modules.identity.domain.model.Device;
import com.mosaicglobal.finance.modules.identity.domain.model.DeviceId;

final class DeviceMapper {

  private DeviceMapper() {}

  static Device toDevice(DeviceDescriptor descriptor) {
    return new Device(
        new DeviceId(descriptor.deviceId()), descriptor.deviceName(), descriptor.platform());
  }
}

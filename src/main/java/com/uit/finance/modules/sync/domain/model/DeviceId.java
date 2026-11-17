package com.uit.finance.modules.sync.domain.model;

import com.uit.finance.modules.sync.domain.exception.InvalidBatchException;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Id của bản cài app trên một máy, chỉ dùng cho nhật ký (ADR-006 B9). */
public record DeviceId(String value) {

  private static final Pattern SHAPE = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

  public DeviceId {
    if (value == null || !SHAPE.matcher(value).matches()) {
      throw new InvalidBatchException("deviceId is missing or malformed");
    }
  }

  public static DeviceId of(@Nullable String raw) {
    return new DeviceId(raw);
  }
}

package com.uit.finance.modules.ledger.adapter.in.sync.support;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Tên field của một schema trong {@code api/openapi.yaml}, đọc từ model sinh ra. */
public final class ContractFields {

  private ContractFields() {}

  public static Set<String> of(Class<?> generatedModel) {
    return Arrays.stream(generatedModel.getDeclaredMethods())
        .map(method -> method.getAnnotation(JsonProperty.class))
        .filter(java.util.Objects::nonNull)
        .map(JsonProperty::value)
        .collect(Collectors.toUnmodifiableSet());
  }
}

package com.mosaicglobal.finance.modules.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailTest {

  @Test
  void normalisesCaseAndWhitespace() {
    assertThat(Email.of("  Alice@Example.COM ")).isEqualTo(new Email("alice@example.com"));
  }

  @Test
  void rejectsMalformedAddresses() {
    assertThatThrownBy(() -> Email.of("not-an-email")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Email.of(" ")).isInstanceOf(IllegalArgumentException.class);
  }
}

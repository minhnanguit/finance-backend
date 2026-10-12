package com.uit.finance.modules.ledger.domain.model.category;

import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.modules.ledger.domain.model.LedgerFixtures;
import com.uit.finance.shared.kernel.UserId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CategoryIdTest {

  private static final UserId USER = UserId.of("00000000-0000-0000-0000-000000000001");

  @Test
  @DisplayName("UUIDv5 khớp vector chuẩn (Python uuid5(NAMESPACE_DNS, 'python.org'))")
  void matchesReferenceVector() {
    UUID dns = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

    UUID id = NameBasedUuid.v5(dns, "python.org");

    assertThat(id).isEqualTo(UUID.fromString("886313e1-3b8a-5372-9b90-0c9aee199e5d"));
    assertThat(id.version()).isEqualTo(5);
    assertThat(id.variant()).isEqualTo(2);
  }

  @Test
  @DisplayName("id danh mục mặc định cố định theo namespace ADR-005 §6 — đổi là seed trùng")
  void templateIdsAreFrozen() {
    assertThat(CategoryId.forTemplate(USER, new TemplateKey("fee")).value())
        .isEqualTo(UUID.fromString("7ef422f4-5395-5a91-9ee4-e594b9e94b83"));
    assertThat(CategoryId.forTemplate(USER, new TemplateKey("food")).value())
        .isEqualTo(UUID.fromString("0ce11766-a6f7-5913-bf80-b72e0afe401e"));
  }

  @Test
  @DisplayName("mỗi user một bộ id riêng")
  void idsDifferPerUser() {
    TemplateKey fee = new TemplateKey("fee");

    assertThat(CategoryId.forTemplate(LedgerFixtures.ANN, fee))
        .isNotEqualTo(CategoryId.forTemplate(LedgerFixtures.BOB, fee))
        .isEqualTo(CategoryId.forTemplate(LedgerFixtures.ANN, fee));
  }
}

package com.uit.finance.modules.ledger.domain.model;

import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.ANN;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.BOB;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.NOW;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.NO_PARENT;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.assertRejected;
import static com.uit.finance.modules.ledger.domain.model.LedgerFixtures.category;
import static org.assertj.core.api.Assertions.assertThat;

import com.uit.finance.modules.ledger.domain.exception.ArchivedException;
import com.uit.finance.modules.ledger.domain.exception.EntityDeletedException;
import com.uit.finance.modules.ledger.domain.exception.InUseException;
import com.uit.finance.modules.ledger.domain.exception.InvalidParentException;
import com.uit.finance.modules.ledger.domain.exception.KindImmutableException;
import com.uit.finance.modules.ledger.domain.exception.LedgerNotFoundException;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CategoryTest {

  private static CategoryDetails details(String name, Category parent) {
    return CategoryDetails.parse(name, parent.getId().value(), null, null);
  }

  private static Function<CategoryId, Category> only(Category parent) {
    return id -> {
      assertThat(id).isEqualTo(parent.getId());
      return parent;
    };
  }

  private static Category childOf(Category parent) {
    return Category.create(
        new CategoryId(UUID.randomUUID()),
        ANN,
        parent.getKind(),
        details("Cà phê", parent),
        only(parent));
  }

  @Test
  @DisplayName("tạo danh mục con dưới danh mục gốc cùng loại")
  void createsChild() {
    Category food = category(ANN, CategoryKind.EXPENSE);

    Category coffee = childOf(food);

    assertThat(coffee.getDetails().parentId()).isEqualTo(food.getId());
  }

  @Test
  @DisplayName("tối đa 2 cấp: không tạo con dưới một danh mục con")
  void atMostTwoLevels() {
    Category food = category(ANN, CategoryKind.EXPENSE);
    Category coffee = childOf(food);

    assertRejected(() -> childOf(coffee), InvalidParentException.CODE);
  }

  @Test
  @DisplayName("cha phải cùng loại thu/chi")
  void parentMustShareKind() {
    Category salary = category(ANN, CategoryKind.INCOME);

    assertRejected(
        () ->
            Category.create(
                new CategoryId(UUID.randomUUID()),
                ANN,
                CategoryKind.EXPENSE,
                details("Cà phê", salary),
                only(salary)),
        InvalidParentException.CODE);
  }

  @Test
  @DisplayName("tự làm cha của chính mình bị chặn trước khi load gì")
  void cannotParentItself() {
    CategoryId id = new CategoryId(UUID.randomUUID());

    assertRejected(
        () ->
            Category.create(
                id,
                ANN,
                CategoryKind.EXPENSE,
                CategoryDetails.parse("Vòng", id.value(), null, null),
                NO_PARENT),
        InvalidParentException.CODE);
  }

  @Test
  @DisplayName("cha đã archive thì không gắn con mới; cha của user khác là not_found")
  void parentMustBeUsable() {
    Category archived = category(ANN, CategoryKind.EXPENSE);
    archived.changeArchived(true, NOW);
    Category foreign = category(BOB, CategoryKind.EXPENSE);

    assertRejected(() -> childOf(archived), ArchivedException.CODE);
    assertRejected(() -> childOf(foreign), LedgerNotFoundException.CODE);
  }

  @Test
  @DisplayName("danh mục đang có con thì không thành con của danh mục khác")
  void parentWithChildrenCannotMoveUnder() {
    Category food = category(ANN, CategoryKind.EXPENSE);
    Category drinks = category(ANN, CategoryKind.EXPENSE);

    assertRejected(
        () -> drinks.revise(CategoryKind.EXPENSE, details("Đồ uống", food), only(food), () -> true),
        InvalidParentException.CODE);
  }

  @Test
  @DisplayName("đổi tên không load cha, không hỏi danh mục con")
  void renameSkipsParentChecks() {
    Category food = category(ANN, CategoryKind.EXPENSE);

    boolean changed =
        food.revise(
            CategoryKind.EXPENSE,
            CategoryDetails.parse("Ăn ngoài", null, "restaurant", "#f97316"),
            NO_PARENT,
            () -> {
              throw new AssertionError("children must not be queried");
            });

    assertThat(changed).isTrue();
    assertThat(food.getDetails().color().value()).isEqualTo("#F97316");
  }

  @Test
  @DisplayName("kind bất biến")
  void kindIsImmutable() {
    Category food = category(ANN, CategoryKind.EXPENSE);

    assertRejected(
        () -> food.revise(CategoryKind.INCOME, food.getDetails(), NO_PARENT, () -> false),
        KindImmutableException.CODE);
  }

  @Test
  @DisplayName("còn dùng thì không xoá; đã xoá thì không sửa")
  void deleteRules() {
    Category used = category(ANN, CategoryKind.EXPENSE);
    Category fresh = category(ANN, CategoryKind.EXPENSE);

    assertRejected(() -> used.delete(NOW, () -> true), InUseException.CODE);
    assertThat(fresh.delete(NOW, () -> false)).isTrue();
    assertThat(fresh.delete(NOW, () -> true)).isFalse();
    assertRejected(
        () -> fresh.revise(CategoryKind.EXPENSE, fresh.getDetails(), NO_PARENT, () -> false),
        EntityDeletedException.CODE);
  }

  @Test
  @DisplayName("seed dựng từ mẫu, id theo UUIDv5, giữ template_key")
  void seedFromTemplate() {
    Category fee = Category.seed(ANN, DefaultCategoryTemplate.FEE);

    assertThat(fee.getId()).isEqualTo(CategoryId.forTemplate(ANN, new TemplateKey("fee")));
    assertThat(fee.getKind()).isEqualTo(CategoryKind.EXPENSE);
    assertThat(fee.getTemplateKey()).isEqualTo(new TemplateKey("fee"));
    assertThat(fee.getDetails().name().value()).isEqualTo("Phí giao dịch");
  }
}

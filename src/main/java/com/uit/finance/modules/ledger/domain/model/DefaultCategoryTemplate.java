package com.uit.finance.modules.ledger.domain.model;

/**
 * Bộ danh mục mặc định v1 (ADR-005 §6). Mỗi user nhận một bản copy riêng, sửa thoải mái.
 *
 * <p>Thêm mẫu mới = thêm một dòng, user cũ nhận ở lần seed kế tiếp. **Không đổi {@code key} của mẫu
 * đã phát hành**: key nằm trong UUIDv5 của danh mục, đổi là user nhận thêm một bản trùng.
 */
public enum DefaultCategoryTemplate {
  FOOD("food", CategoryKind.EXPENSE, "Ăn uống", "restaurant", "#F97316"),
  TRANSPORT("transport", CategoryKind.EXPENSE, "Di chuyển", "directions_car", "#3B82F6"),
  SHOPPING("shopping", CategoryKind.EXPENSE, "Mua sắm", "shopping_bag", "#EC4899"),
  BILLS("bills", CategoryKind.EXPENSE, "Hoá đơn", "receipt", "#EAB308"),
  HOUSING("housing", CategoryKind.EXPENSE, "Nhà cửa", "home", "#8B5CF6"),
  HEALTH("health", CategoryKind.EXPENSE, "Sức khoẻ", "favorite", "#EF4444"),
  EDUCATION("education", CategoryKind.EXPENSE, "Giáo dục", "school", "#06B6D4"),
  ENTERTAINMENT("entertainment", CategoryKind.EXPENSE, "Giải trí", "movie", "#A855F7"),
  FAMILY("family", CategoryKind.EXPENSE, "Gia đình", "family", "#F43F5E"),
  /** Phí chuyển khoản (ADR-005 D3). Bắt buộc phải có. */
  FEE("fee", CategoryKind.EXPENSE, "Phí giao dịch", "percent", "#64748B"),
  OTHER_EXPENSE("other_expense", CategoryKind.EXPENSE, "Chi khác", "more_horiz", "#9CA3AF"),
  SALARY("salary", CategoryKind.INCOME, "Lương", "payments", "#22C55E"),
  BONUS("bonus", CategoryKind.INCOME, "Thưởng", "redeem", "#10B981"),
  INVESTMENT("investment", CategoryKind.INCOME, "Đầu tư", "trending_up", "#14B8A6"),
  GIFT("gift", CategoryKind.INCOME, "Được tặng", "card_giftcard", "#84CC16"),
  OTHER_INCOME("other_income", CategoryKind.INCOME, "Thu khác", "more_horiz", "#6B7280");

  private final TemplateKey key;
  private final CategoryKind kind;
  private final CategoryDetails details;

  DefaultCategoryTemplate(
      String key, CategoryKind kind, String displayName, String icon, String color) {
    this.key = new TemplateKey(key);
    this.kind = kind;
    this.details =
        new CategoryDetails(
            new LedgerName(displayName), null, new IconName(icon), new ColorHex(color));
  }

  public TemplateKey key() {
    return key;
  }

  public CategoryKind kind() {
    return kind;
  }

  public CategoryDetails details() {
    return details;
  }
}

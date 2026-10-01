package com.uit.finance.modules.ledger.application.service.category;

import com.uit.finance.modules.ledger.application.port.in.category.SeedDefaultCategoriesUseCase;
import com.uit.finance.modules.ledger.application.port.out.category.SeedCategoriesPort;
import com.uit.finance.modules.ledger.domain.model.category.Category;
import com.uit.finance.modules.ledger.domain.model.category.DefaultCategoryTemplate;
import com.uit.finance.shared.kernel.UserId;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Không áp giới hạn 300 danh mục: bộ mặc định do server sở hữu, nhỏ, và chạy trước mọi danh mục của
 * user ở lần sync đầu tiên (ADR-002 §5).
 */
@Service
@Transactional
class SeedDefaultCategoriesService implements SeedDefaultCategoriesUseCase {

  private final SeedCategoriesPort seedCategories;

  SeedDefaultCategoriesService(SeedCategoriesPort seedCategories) {
    this.seedCategories = seedCategories;
  }

  @Override
  public int seed(UserId userId) {
    List<Category> defaults =
        Arrays.stream(DefaultCategoryTemplate.values())
            .map(template -> Category.seed(userId, template))
            .toList();
    return seedCategories.insertAllIfAbsent(userId, defaults);
  }
}

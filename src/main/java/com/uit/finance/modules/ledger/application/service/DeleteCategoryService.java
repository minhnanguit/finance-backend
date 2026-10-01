package com.uit.finance.modules.ledger.application.service;

import com.uit.finance.modules.ledger.application.port.in.CategoryView;
import com.uit.finance.modules.ledger.application.port.in.DeleteCategoryUseCase;
import com.uit.finance.modules.ledger.application.port.out.CategoryUsagePort;
import com.uit.finance.modules.ledger.application.port.out.LoadCategoryPort;
import com.uit.finance.modules.ledger.application.port.out.SaveCategoryPort;
import com.uit.finance.modules.ledger.domain.exception.LedgerNotFoundException;
import com.uit.finance.modules.ledger.domain.model.Category;
import com.uit.finance.modules.ledger.domain.model.CategoryId;
import com.uit.finance.modules.ledger.domain.model.LedgerEntity;
import com.uit.finance.shared.kernel.UserId;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class DeleteCategoryService implements DeleteCategoryUseCase {

  private final LoadCategoryPort loadCategory;
  private final SaveCategoryPort saveCategory;
  private final CategoryUsagePort usage;
  private final Clock clock;

  DeleteCategoryService(
      LoadCategoryPort loadCategory,
      SaveCategoryPort saveCategory,
      CategoryUsagePort usage,
      Clock clock) {
    this.loadCategory = loadCategory;
    this.saveCategory = saveCategory;
    this.usage = usage;
    this.clock = clock;
  }

  @Override
  public CategoryView delete(DeleteCategoryCommand command) {
    UserId owner = command.userId();
    CategoryId id = CategoryId.of(command.categoryId());
    Category category =
        loadCategory
            .find(owner, id)
            .orElseThrow(() -> new LedgerNotFoundException(LedgerEntity.CATEGORY, id.value()));

    boolean deleted =
        category.delete(
            clock.instant(),
            () -> usage.isReferenced(owner, id) || usage.hasActiveChildren(owner, id));
    if (deleted) {
      saveCategory.update(owner, category);
    }
    return CategoryView.from(category);
  }
}

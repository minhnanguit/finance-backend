package com.uit.finance.modules.ledger.application.service;

import com.uit.finance.modules.ledger.application.port.in.CategoryView;
import com.uit.finance.modules.ledger.application.port.in.UpdateCategoryUseCase;
import com.uit.finance.modules.ledger.application.port.out.CategoryUsagePort;
import com.uit.finance.modules.ledger.application.port.out.LoadCategoryPort;
import com.uit.finance.modules.ledger.application.port.out.SaveCategoryPort;
import com.uit.finance.modules.ledger.domain.exception.LedgerNotFoundException;
import com.uit.finance.modules.ledger.domain.model.Category;
import com.uit.finance.modules.ledger.domain.model.CategoryId;
import com.uit.finance.modules.ledger.domain.model.LedgerEntity;
import com.uit.finance.shared.kernel.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class UpdateCategoryService implements UpdateCategoryUseCase {

  private final LoadCategoryPort loadCategory;
  private final SaveCategoryPort saveCategory;
  private final CategoryUsagePort usage;

  UpdateCategoryService(
      LoadCategoryPort loadCategory, SaveCategoryPort saveCategory, CategoryUsagePort usage) {
    this.loadCategory = loadCategory;
    this.saveCategory = saveCategory;
    this.usage = usage;
  }

  @Override
  public CategoryView update(UpdateCategoryCommand command) {
    UserId owner = command.userId();
    CategoryId id = CategoryId.of(command.categoryId());
    Category category =
        loadCategory
            .find(owner, id)
            .orElseThrow(() -> new LedgerNotFoundException(LedgerEntity.CATEGORY, id.value()));

    boolean changed =
        category.revise(
            LedgerCommandMapper.toKind(command.fields()),
            LedgerCommandMapper.toDetails(command.fields()),
            CategoryParents.of(loadCategory, owner),
            () -> usage.hasActiveChildren(owner, id));
    if (changed) {
      saveCategory.update(owner, category);
    }
    return CategoryView.from(category);
  }
}

package com.uit.finance.modules.ledger.application.service;

import com.uit.finance.modules.ledger.application.port.in.ArchiveCategoryUseCase;
import com.uit.finance.modules.ledger.application.port.in.CategoryView;
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
class ArchiveCategoryService implements ArchiveCategoryUseCase {

  private final LoadCategoryPort loadCategory;
  private final SaveCategoryPort saveCategory;
  private final Clock clock;

  ArchiveCategoryService(
      LoadCategoryPort loadCategory, SaveCategoryPort saveCategory, Clock clock) {
    this.loadCategory = loadCategory;
    this.saveCategory = saveCategory;
    this.clock = clock;
  }

  @Override
  public CategoryView setArchived(ArchiveCategoryCommand command) {
    UserId owner = command.userId();
    CategoryId id = CategoryId.of(command.categoryId());
    Category category =
        loadCategory
            .find(owner, id)
            .orElseThrow(() -> new LedgerNotFoundException(LedgerEntity.CATEGORY, id.value()));

    if (category.changeArchived(command.archived(), clock.instant())) {
      saveCategory.update(owner, category);
    }
    return CategoryView.from(category);
  }
}

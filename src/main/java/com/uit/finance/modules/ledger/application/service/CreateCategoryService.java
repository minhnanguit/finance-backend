package com.uit.finance.modules.ledger.application.service;

import com.uit.finance.modules.ledger.application.port.in.CategoryView;
import com.uit.finance.modules.ledger.application.port.in.CreateCategoryUseCase;
import com.uit.finance.modules.ledger.application.port.out.CategoryUsagePort;
import com.uit.finance.modules.ledger.application.port.out.LoadCategoryPort;
import com.uit.finance.modules.ledger.application.port.out.SaveCategoryPort;
import com.uit.finance.modules.ledger.domain.exception.LedgerNotFoundException;
import com.uit.finance.modules.ledger.domain.model.Category;
import com.uit.finance.modules.ledger.domain.model.CategoryDetails;
import com.uit.finance.modules.ledger.domain.model.CategoryId;
import com.uit.finance.modules.ledger.domain.model.CategoryKind;
import com.uit.finance.modules.ledger.domain.model.LedgerEntity;
import com.uit.finance.modules.ledger.domain.model.LedgerLimits;
import com.uit.finance.shared.kernel.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class CreateCategoryService implements CreateCategoryUseCase {

  private final LoadCategoryPort loadCategory;
  private final SaveCategoryPort saveCategory;
  private final CategoryUsagePort usage;

  CreateCategoryService(
      LoadCategoryPort loadCategory, SaveCategoryPort saveCategory, CategoryUsagePort usage) {
    this.loadCategory = loadCategory;
    this.saveCategory = saveCategory;
    this.usage = usage;
  }

  @Override
  public CategoryView create(CreateCategoryCommand command) {
    UserId owner = command.userId();
    CategoryId id = CategoryId.of(command.categoryId());
    CategoryKind kind = LedgerCommandMapper.toKind(command.fields());
    CategoryDetails details = LedgerCommandMapper.toDetails(command.fields());

    Category category =
        CreateOnce.run(
            () -> loadCategory.find(owner, id),
            () -> {
              LedgerLimits.ensureCanAddCategory(usage.countCategories(owner));
              return Category.create(
                  id, owner, kind, details, CategoryParents.of(loadCategory, owner));
            },
            created -> saveCategory.insertIfAbsent(owner, created),
            () -> new LedgerNotFoundException(LedgerEntity.CATEGORY, id.value()));
    category.requireNotDeleted();
    return CategoryView.from(category);
  }
}

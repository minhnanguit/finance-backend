package com.uit.finance.modules.ledger.application.service.category;

import com.uit.finance.modules.ledger.application.port.in.category.CategoryView;
import com.uit.finance.modules.ledger.application.port.in.category.CreateCategoryUseCase;
import com.uit.finance.modules.ledger.application.port.out.category.CategoryUsagePort;
import com.uit.finance.modules.ledger.application.port.out.category.LoadCategoryPort;
import com.uit.finance.modules.ledger.application.port.out.category.SaveCategoryPort;
import com.uit.finance.modules.ledger.application.service.shared.CreateOnce;
import com.uit.finance.modules.ledger.application.service.shared.LedgerCommandMapper;
import com.uit.finance.modules.ledger.domain.exception.LedgerNotFoundException;
import com.uit.finance.modules.ledger.domain.model.category.Category;
import com.uit.finance.modules.ledger.domain.model.category.CategoryDetails;
import com.uit.finance.modules.ledger.domain.model.category.CategoryId;
import com.uit.finance.modules.ledger.domain.model.category.CategoryKind;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerEntity;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerLimits;
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

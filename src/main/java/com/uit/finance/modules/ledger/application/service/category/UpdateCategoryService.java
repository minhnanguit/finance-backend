package com.uit.finance.modules.ledger.application.service.category;

import com.uit.finance.modules.ledger.application.port.in.category.CategoryView;
import com.uit.finance.modules.ledger.application.port.in.category.UpdateCategoryUseCase;
import com.uit.finance.modules.ledger.application.port.out.category.CategoryUsagePort;
import com.uit.finance.modules.ledger.application.port.out.category.LoadCategoryPort;
import com.uit.finance.modules.ledger.application.port.out.category.SaveCategoryPort;
import com.uit.finance.modules.ledger.application.service.shared.LedgerCommandMapper;
import com.uit.finance.modules.ledger.domain.exception.LedgerNotFoundException;
import com.uit.finance.modules.ledger.domain.model.category.Category;
import com.uit.finance.modules.ledger.domain.model.category.CategoryId;
import com.uit.finance.modules.ledger.domain.model.shared.LedgerEntity;
import com.uit.finance.shared.kernel.DomainEventPublisher;
import com.uit.finance.shared.kernel.UserId;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class UpdateCategoryService implements UpdateCategoryUseCase {

  private final LoadCategoryPort loadCategory;
  private final SaveCategoryPort saveCategory;
  private final CategoryUsagePort usage;
  private final DomainEventPublisher events;
  private final Clock clock;

  UpdateCategoryService(
      LoadCategoryPort loadCategory,
      SaveCategoryPort saveCategory,
      CategoryUsagePort usage,
      DomainEventPublisher events,
      Clock clock) {
    this.loadCategory = loadCategory;
    this.saveCategory = saveCategory;
    this.usage = usage;
    this.events = events;
    this.clock = clock;
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
            () -> usage.hasActiveChildren(owner, id),
            clock.instant());
    if (changed) {
      saveCategory.update(owner, category);
      events.publishAll(category.pullDomainEvents());
    }
    return CategoryView.from(category);
  }
}

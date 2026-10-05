package com.uit.finance.modules.ledger.application.service.category;

import com.uit.finance.modules.ledger.application.port.in.category.CategoryChangeFeedUseCase;
import com.uit.finance.modules.ledger.application.port.in.category.CategoryView;
import com.uit.finance.modules.ledger.application.port.out.category.LoadCategoryChangesPort;
import com.uit.finance.modules.ledger.application.port.out.shared.Sequenced;
import com.uit.finance.modules.ledger.domain.model.category.Category;
import com.uit.finance.modules.ledger.domain.model.category.CategoryId;
import com.uit.finance.shared.kernel.UserId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class CategoryChangeFeedService implements CategoryChangeFeedUseCase {

  private final LoadCategoryChangesPort changes;

  CategoryChangeFeedService(LoadCategoryChangesPort changes) {
    this.changes = changes;
  }

  @Override
  public List<CategoryChange> since(UserId owner, long afterSeq, int limit) {
    return changes.changesSince(owner, afterSeq, limit).stream()
        .map(CategoryChangeFeedService::toChange)
        .toList();
  }

  @Override
  public Optional<CategoryChange> find(UserId owner, UUID categoryId) {
    return changes
        .findSequenced(owner, CategoryId.of(categoryId))
        .map(CategoryChangeFeedService::toChange);
  }

  private static CategoryChange toChange(Sequenced<Category> sequenced) {
    return new CategoryChange(CategoryView.from(sequenced.value()), sequenced.changeSeq());
  }
}

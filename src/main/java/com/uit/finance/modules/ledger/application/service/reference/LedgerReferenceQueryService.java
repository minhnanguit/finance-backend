package com.uit.finance.modules.ledger.application.service.reference;

import com.uit.finance.modules.ledger.application.port.in.reference.CategoryRef;
import com.uit.finance.modules.ledger.application.port.in.reference.LedgerReferenceQuery;
import com.uit.finance.modules.ledger.application.port.out.category.LoadCategoryPort;
import com.uit.finance.modules.ledger.domain.model.category.CategoryId;
import com.uit.finance.shared.kernel.UserId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class LedgerReferenceQueryService implements LedgerReferenceQuery {

  private final LoadCategoryPort loadCategory;

  LedgerReferenceQueryService(LoadCategoryPort loadCategory) {
    this.loadCategory = loadCategory;
  }

  @Override
  public Optional<CategoryRef> findCategory(UserId owner, UUID categoryId) {
    return loadCategory.find(owner, new CategoryId(categoryId)).map(CategoryRef::from);
  }

  @Override
  public List<CategoryRef> findCategories(UserId owner, Set<UUID> categoryIds) {
    return loadCategory.findAll(owner, categoryIds.stream().map(CategoryId::new).toList()).stream()
        .map(CategoryRef::from)
        .toList();
  }
}

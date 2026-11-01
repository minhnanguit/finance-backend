package com.uit.finance.modules.budget.adapter.in.messaging;

import com.uit.finance.modules.budget.application.port.in.ReconcileBudgetsOfCategoryUseCase;
import com.uit.finance.modules.budget.application.port.in.ReconcileBudgetsOfCategoryUseCase.ReconcileBudgetsOfCategoryCommand;
import com.uit.finance.shared.kernel.UserId;
import com.uit.finance.shared.messaging.EventDeduplicator;
import com.uit.finance.shared.messaging.EventEnvelope;
import com.uit.finance.shared.messaging.InboundEvents;
import java.nio.charset.StandardCharsets;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consume {@code ledger.category.changed}: đọc lại danh mục rồi xoá mềm ngân sách không còn hợp lệ
 * (ADR-007 GS6). Use case tự đọc trạng thái hiện tại nên event trễ, trùng hay sai thứ tự đều an
 * toàn. Lỗi để bay lên: container retry với backoff rồi park message.
 */
@Component
class CategoryChangedListener {

  static final String CONSUMER = BudgetQueues.CATEGORY_CHANGED_QUEUE;

  private final InboundEvents inboundEvents;
  private final EventDeduplicator deduplicator;
  private final ReconcileBudgetsOfCategoryUseCase reconcile;

  CategoryChangedListener(
      InboundEvents inboundEvents,
      EventDeduplicator deduplicator,
      ReconcileBudgetsOfCategoryUseCase reconcile) {
    this.inboundEvents = inboundEvents;
    this.deduplicator = deduplicator;
    this.reconcile = reconcile;
  }

  @RabbitListener(queues = BudgetQueues.CATEGORY_CHANGED_QUEUE)
  void onCategoryChanged(Message message) {
    String json = new String(message.getBody(), StandardCharsets.UTF_8);
    EventEnvelope<CategoryChangedPayload> event =
        inboundEvents.decode(json, CategoryChangedPayload.class);
    CategoryChangedPayload payload = event.payload();
    deduplicator.executeOnce(
        event.eventId(),
        CONSUMER,
        () ->
            reconcile.reconcile(
                new ReconcileBudgetsOfCategoryCommand(
                    new UserId(payload.userId()), payload.categoryId())));
  }
}

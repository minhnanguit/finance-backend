package com.uit.finance.modules.sync.domain.model;

import com.uit.finance.shared.kernel.Ensure;
import org.jspecify.annotations.Nullable;

/** Module sở hữu entity quyết định gì với một op. */
public record OpVerdict(OpOutcome outcome, @Nullable String code) {

  public static final String UNKNOWN_ENTITY = "sync.unknown_entity";
  public static final String SERVER_ERROR = "sync.server_error";

  public OpVerdict {
    Ensure.notNull(outcome, "outcome");
  }

  public static OpVerdict applied() {
    return new OpVerdict(OpOutcome.APPLIED, null);
  }

  public static OpVerdict duplicate() {
    return new OpVerdict(OpOutcome.DUPLICATE, null);
  }

  public static OpVerdict unknownEntity() {
    return new OpVerdict(OpOutcome.REJECTED, UNKNOWN_ENTITY);
  }

  /** Lỗi không lường trước: giữ op để client thử lại thay vì làm hỏng cả batch. */
  public static OpVerdict serverError() {
    return new OpVerdict(OpOutcome.RETRY, SERVER_ERROR);
  }
}

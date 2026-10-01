package com.uit.finance.modules.ledger.application.port.in;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Dữ liệu danh mục client gửi lên, chưa kiểm tra. {@code kind} luôn được gửi, kể cả khi sửa, để
 * server phát hiện ý định đổi loại ({@code ledger.kind_immutable}).
 */
public record CategoryFields(
    @Nullable String kind,
    @Nullable String name,
    @Nullable UUID parentId,
    @Nullable String icon,
    @Nullable String color) {}

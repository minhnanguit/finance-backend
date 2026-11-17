# Rule — Ranh giới kiến trúc (Clean Architecture + Spring Modulith)

Luôn áp dụng. Vi phạm là **fail build** ở `make test`, không phải góp ý review.

## Hình dạng một module

```
modules/<name>/
├─ package-info.java     @ApplicationModule(displayName=..., allowedDependencies={})
├─ domain/               model/ (aggregate, value object) · event/ · service/ · exception/
├─ application/          port/in/ (1 interface / use case + Command record)
│                        port/out/ (port hẹp: LoadXPort, SaveXPort…)
│                        service/ (@Service @Transactional, implement port/in)
└─ adapter/              in/web · in/messaging · in/scheduler
                         out/persistence · out/messaging · out/client · config
```

Chiều phụ thuộc: `adapter → application → domain`. Không bao giờ ngược lại.

## Luật (khớp `ArchitectureRulesTest` + `ModularityTest`)

| # | Luật | Enforce |
|---|---|---|
| 1 | `..domain..` và `..shared.kernel..` không phụ thuộc `org.springframework..`, `jakarta..`, `javax..`, `com.fasterxml..`, `tools.jackson..`, `org.hibernate..`, `com.nimbusds..`, `com.rabbitmq..` | ArchUnit `rule1` |
| 2 | `@Entity` / `@MappedSuperclass` chỉ ở `..adapter.out.persistence..` hoặc `..shared.persistence..` | ArchUnit `rule2` |
| 3 | `..application..` chỉ phụ thuộc: application, domain, `shared.kernel`, `java..`, `org.jspecify..`, `org.slf4j..`, `org.springframework.stereotype..`, `org.springframework.transaction.annotation..` | ArchUnit `rule3` |
| 4 | Layer hướng vào trong; `adapter.in` ↮ `adapter.out` | ArchUnit `rule4a/4b/4c` |
| 5 | Class top-level trong `..application.port..` phải là `interface` hoặc `record` | ArchUnit `rule5` |
| 6 | Module chỉ gọi nhau qua `port/in` public hoặc `domain.event` được `@NamedInterface` | `ApplicationModules.verify()` |
| 7 | `shared.web` / `shared.persistence` / `shared.security` / `shared.messaging` / `shared.sync` chỉ cho adapter dùng — domain/application/kernel cấm | ArchUnit `rule7` |
| 8 | `@Transactional` (class hoặc method) chỉ trong `..application.service..` | ArchUnit `rule8` |
| 10 | Mọi method của interface trong port out của module dữ liệu-theo-user (`USER_SCOPED_OUT_PORTS`, hiện là `ledger`) phải có tham số `UserId` (ADR-006 B1) | ArchUnit `rule10` |

Module mới có bảng gắn user (budget, recurring...) thì thêm package `port.out` của nó vào `USER_SCOPED_OUT_PORTS`.

> ArchUnit đọc **bytecode**: một `import` thừa không bị bắt, chỉ dependency thật (field, param, annotation, call) mới vi phạm.

## Khai báo module

`package-info.java` của module:

```java
@org.springframework.modulith.ApplicationModule(
    displayName = "Identity",
    allowedDependencies = {})   // {} = chỉ được dùng shared
package com.mosaicglobal.finance.modules.identity;
```

- Muốn module khác consume event của mình → gói `domain/event` có `package-info.java` với `@NamedInterface("events")`.
- Muốn dùng event của module khác → thêm `"<module>::events"` vào `allowedDependencies`.
- Detection strategy là `explicitly-annotated` (`application.properties`): **không có `@ApplicationModule` thì không phải module**.

## Ngoại lệ đã được ghi nhận

- `EventDeduplicator` dùng `TransactionTemplate` lập trình thay vì `@Transactional` — cố ý, vì nó là hạ tầng chứ không phải application service.
- `shared/` không phải module Modulith; nó là nền tảng cho mọi module.

## Khi cần phá luật

Không tự phá. Viết ADR mới trong `docs/adr/` theo mẫu ADR-001..004 và nêu rõ đánh đổi, rồi mới sửa `ArchitectureRulesTest`.

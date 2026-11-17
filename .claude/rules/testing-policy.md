# Rule — Testing policy

Hai suite tách bạch, do `jvm-test-suite` cấu hình trong `build.gradle.kts`.

| Suite | Lệnh | Cần Docker | Chứa gì |
|---|---|---|---|
| `test` | `make test` | Không | Unit domain (JUnit thuần) · use case với fake port · `ArchitectureRulesTest` · `ModularityTest` · filter test |
| `integrationTest` | `make itest` | **Có** | Spring context + Testcontainers (Postgres, RabbitMQ) + `spring-security-test` + `awaitility` |

`tasks.check` phụ thuộc `integrationTest`. `integrationTest` `shouldRunAfter(test)`.

## Viết test ở đâu

| Đối tượng | Nơi | Cách |
|---|---|---|
| Value object, aggregate | `src/test/…/domain/model/` | JUnit thuần, không Spring. Ví dụ `EmailTest`, `UserTest` |
| Application service | `src/test/…/application/service/` | Fake in-memory cho mọi port out. Mẫu: `IdentityFakes` |
| Luật kiến trúc | `src/test/…/architecture/` | Đã có; chỉ sửa khi có ADR |
| Adapter web/persistence/messaging, luồng end-to-end | `src/integrationTest/` | `TestcontainersConfiguration`, `ApiClient`. Mẫu: `JitProvisioningIT`, `UserRegisteredEventIT`. Token có signature thật lấy từ `TestIdentityProvider` |

## Quy tắc

- **Không** dùng `@SpringBootTest` trong `src/test` — suite đó phải chạy được khi không có Docker và không có Spring context.
- Fake > Mockito cho port out: fake là class nhỏ implement port, dễ đọc, không stub-hell.
- Test bất đồng bộ (outbox → RabbitMQ → consumer) dùng `awaitility`, không `Thread.sleep`.
- Clock trong test là `Clock.fixed`, không dùng giờ hệ thống.
- Thêm module mới ⇒ bắt buộc có: test domain, test use case với fake, và ít nhất 1 IT phủ luồng thật.
- Report lỗi: `build/reports/tests` (CI upload khi fail).

## Định nghĩa "xong"

Trước khi báo hoàn thành, phải chạy và báo cáo trung thực kết quả:

```bash
make fmt
make test     # luôn luôn
make itest    # nếu đụng adapter / migration / messaging / security
```

Nếu không chạy được (thiếu Docker…) thì nói rõ là **chưa chạy**, đừng suy đoán là xanh.

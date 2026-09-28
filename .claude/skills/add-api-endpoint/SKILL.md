---
name: add-api-endpoint
description: Thêm hoặc sửa một endpoint HTTP trong finance-backend theo quy trình contract-first (openapi.yaml → generated interface → controller → use case). Dùng khi task là "thêm API", "sửa response", "thêm field vào request", "expose endpoint mới".
---

# Thêm / sửa API endpoint

Bắt buộc đọc trước: `.claude/rules/contract-first-api.md`, `.claude/rules/architecture-boundaries.md`.

## Thứ tự làm (không đảo)

### 1. Sửa hợp đồng `api/openapi.yaml`

```yaml
  /api/v1/<resource>:
    post:
      tags: [<tag>]
      operationId: <camelCase>          # → tên method trong interface sinh ra
      summary: <một câu>
      parameters:
        - $ref: '#/components/parameters/IdempotencyKey'   # BẮT BUỘC cho POST/PUT/PATCH
      requestBody: { required: true, content: { application/json: { schema: { $ref: '#/components/schemas/XRequest' } } } }
      responses:
        '200': { description: ..., content: { application/json: { schema: { $ref: '#/components/schemas/XResponse' } } } }
        '400': { $ref: '#/components/responses/BadRequest' }
        '409': { $ref: '#/components/responses/Conflict' }
        '422': { $ref: '#/components/responses/UnprocessableEntity' }
```

- Endpoint không cần token → thêm `security: []` **và** khai trong `shared/security/PublicEndpoints`.
- `tags` quyết định tên interface (`useTags=true`): tag `me` → `MeApi`.
- Tiền: integer minor units + currency ISO-4217. Không float.

### 2. Sinh interface

```bash
make api      # hoặc compileJava, nó tự dependsOn
```

Output: `build/generated/openapi/src/main/java/com/mosaicglobal/finance/api/v1/…`. Đọc interface vừa sinh để biết signature chính xác trước khi viết controller.

### 3. Use case trước, controller sau

`modules/<module>/application/port/in/XUseCase.java`:

```java
public interface XUseCase {
  XResult doIt(XCommand command);
  record XCommand(String a, UUID b) {}
}
```

`application/service/XService.java` — `@Service @Transactional`, package-private, implement interface trên, chỉ gọi port out.
Cần dữ liệu từ ngoài → thêm **port out hẹp** (`LoadXPort`), rồi adapter implement. Không nhét JPA repository vào service.

### 4. Controller

`modules/<module>/adapter/in/web/XController.java`, package-private:

```java
@RestController
class XController implements XApi {
  @Override public ResponseEntity<XResponse> doIt(UUID idempotencyKey, XRequest request) {
    return ResponseEntity.ok(mapper.toResponse(useCase.doIt(mapper.toCommand(request))));
  }
}
```

- Mapper viết tay `XWebMapper` (package-private) ở cùng package.
- Không `@Transactional`, không logic nghiệp vụ, không bắt exception — `ProblemDetailsExceptionHandler` lo.
- Cần user hiện tại: `AuthenticatedUser.requireCurrent()`.

### 5. Lỗi

Ném `DomainException` subclass với `ErrorCategory` + code `<module>.<reason>`. Mapping status đã cố định trong `HttpStatusMapping`; đừng tự set status trong controller cho case lỗi.

### 6. Verify

```bash
make fmt
make test
make itest        # thêm IT trong src/integrationTest, mẫu JitProvisioningIT + ApiClient + TestIdentityProvider (token có signature thật)
```

## Checklist trước khi báo xong

- [ ] `api/openapi.yaml` sửa **trước**, controller implement interface sinh ra (không `@PostMapping` tay)
- [ ] POST/PUT/PATCH có `IdempotencyKey` param trong spec
- [ ] Response lỗi khai đủ trong spec (400/401/404/409/422 tuỳ case)
- [ ] Use case + port out hẹp, `@Transactional` chỉ ở service
- [ ] Unit test use case với fake port + IT phủ HTTP thật
- [ ] Nếu là breaking change → báo user: cần bump major + `/api/v2`, và `finance-mobile` phải re-pin contract

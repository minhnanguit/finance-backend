# Rule — Contract-first API

`api/openapi.yaml` (OpenAPI 3.0.3, `info.version` hiện tại `2.1.0`) là **nguồn sự thật duy nhất**.

## Thứ tự bất biến

1. Sửa `api/openapi.yaml`.
2. `./gradlew compileJava` → openapi-generator (7.14.0, generator `spring`) sinh interface + DTO vào
   `build/generated/openapi`, package `com.mosaicglobal.finance.api.v1` / `.model`.
3. Controller `implements` interface sinh ra. **Contract đổi mà không implement → không compile.** Đó là cơ chế bảo vệ, đừng bypass.

Cấm: viết `@GetMapping` / `@PostMapping` thủ công cho endpoint nghiệp vụ; `springdoc.api-docs.enabled=false` nên spec **không** được sinh ngược từ code.

## Quy ước trong spec

| Chủ đề | Quy ước |
|---|---|
| Path | `/api/v1/...`. Breaking change → major mới + `/api/v2`; giữ `/v1` ít nhất 2 release |
| Write endpoint | POST/PUT/PATCH **phải** có `parameters: - $ref: '#/components/parameters/IdempotencyKey'` |
| Lỗi | RFC 7807 `application/problem+json` + field `code` máy đọc được |
| Tiền | Không dùng float: số nguyên minor units + mã ISO-4217 |
| Paging | Cursor-based (xem `shared/web/Cursor`, `CursorPage`) |
| Endpoint public | Thêm `security: []` trong spec **và** khai báo trong `shared/security/PublicEndpoints` |

## Controller

- Đặt ở `modules/<name>/adapter/in/web/`, **package-private** (`class MeController implements MeApi`).
- Mỏng: map DTO ↔ Command, gọi use case, trả `ResponseEntity`. Không logic nghiệp vụ, không `@Transactional`.
- Mapper web viết tay (`UserWebMapper`) — MapStruct chỉ chấp nhận cho DTO↔DTO phẳng.
- Tham số `UUID idempotencyKey` có trong signature chỉ vì contract mô tả nó; giá trị do `IdempotencyFilter` xử lý, controller bỏ qua.

## Phát hành

- `processResources` copy spec vào `static/` → Swagger UI đọc `/openapi.yaml`.
- Tag `vX.Y.Z` → job `release-contract` attach spec vào GitHub Release.
- `finance-mobile/api/openapi.yaml` là bản copy nguyên văn. **Không sửa spec ở repo mobile.** Mọi thay đổi API bắt đầu từ đây.

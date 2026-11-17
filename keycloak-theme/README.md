# Finance Keycloak theme

Giao diện đăng nhập và đăng ký cho Finance, viết bằng Keycloakify 11, React, Vite và Tailwind CSS. Theme nhắm tới Keycloak 26.x và có tên là `finance`.

## Phạm vi hiện tại

-   Layout chung cho các trang auth (`Template.tsx`).
-   Login có email/tên đăng nhập, mật khẩu, hiện/ẩn mật khẩu, ghi nhớ đăng nhập và quên mật khẩu.
-   Register lấy field động từ Keycloak User Profile; không viết cứng field.
-   Responsive cho Custom Tab trên điện thoại, dark mode theo hệ điều hành.
-   Hai ngôn ngữ `vi` và `en`.
-   Reset/update password, verify email, info và error dùng component Keycloakify an toàn trong layout Finance.
-   Email verify/reset có subject và nội dung `vi`/`en` trong email theme `finance`.
-   Font Montserrat được đóng gói trong JAR; không tải font, script hay ảnh từ CDN.

## Chạy local

Yêu cầu Node.js 20+ và npm.

```bash
npm install
npm run storybook
```

Storybook chạy tại `http://localhost:6006` với story cho Login, Login lỗi, Register và bản tiếng Việt.

## Kiểm tra và build

```bash
npm run build
npm run build-storybook
npx playwright install chromium
npm run test:e2e
npm run build-keycloak-theme
```

File dùng cho Keycloak 26 được tạo tại:

```text
dist_keycloak/keycloak-theme-for-kc-all-other-versions.jar
```

Để test với Keycloak dev, build theme rồi mount file trên vào `/opt/keycloak/providers/finance-theme.jar`, sau đó đặt Login theme của realm thành `finance`. Không commit các thư mục output `dist`, `dist_keycloak` hoặc `storybook-static`.

Baseline ảnh của Playwright nằm trong `e2e/theme.spec.ts-snapshots`. CI chỉ so sánh và không tự cập nhật baseline. Khi thay đổi giao diện có chủ đích, tạo và xem lại baseline Linux bằng:

```bash
docker build -f e2e/Dockerfile -t finance-keycloak-theme-e2e .
docker run --rm -v "$PWD/e2e/theme.spec.ts-snapshots:/theme/e2e/theme.spec.ts-snapshots" finance-keycloak-theme-e2e
```

## Image production

Dockerfile tự build JAR từ source rồi tạo image Keycloak đã tối ưu:

```bash
docker build -f keycloak-theme/Dockerfile -t finance-keycloak:26.7.4-theme keycloak-theme
```

`make up` thực hiện bước build image này và chạy cấu hình realm lặp-an-toàn, nên hoạt động trên checkout sạch và không yêu cầu xoá volume Keycloak hiện có.

## Token thiết kế

Nguồn token nằm tại `tokens/tokens.json`, đồng bộ với màu Material/Finance trong `finance-mobile/core/designsystem/theme/Color.kt`. `scripts/generate-tokens.mjs` sinh CSS variables trước mỗi lần build.

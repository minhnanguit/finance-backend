-- Module: identity — chuyển sang Keycloak làm IdP (ADR-004).
-- Backend không còn giữ password hay refresh token.
--
-- ⚠️ MẤT DỮ LIỆU CÓ CHỦ ĐÍCH: user cũ do auth tự viết tạo ra không có account Keycloak
-- tương ứng, và password của họ bị xoá ở migration này nên họ không login lại được.
-- Hệ thống chưa từng deploy và chưa có user thật, nên xoá là đúng. Nếu về sau có dữ liệu
-- thật thì phải làm expand/contract kèm bước import user sang Keycloak trước.

DROP TABLE IF EXISTS refresh_tokens;

DELETE FROM users;

ALTER TABLE users DROP COLUMN password_hash;
ALTER TABLE users ADD COLUMN external_subject VARCHAR(255) NOT NULL;

-- Unique index KHÔNG partial: user đã soft delete vẫn giữ subject, nên nếu họ login lại
-- bằng đúng account Keycloak đó thì ta tìm ra record cũ thay vì tạo record thứ hai.
-- Đây cũng là index mà ON CONFLICT trong UserJpaRepository.insertIfAbsent dựa vào.
CREATE UNIQUE INDEX ux_users_external_subject ON users (external_subject);

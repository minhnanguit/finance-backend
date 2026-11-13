-- Module: identity — xoá tài khoản (ADR-008 A4, A5, A8).
--
-- Vòng đời: ACTIVE → DELETING (user yêu cầu, email/tên bị thay bằng giá trị không còn là của ai) → DELETED
-- (hết thời gian chờ, các module đã được báo xoá thật dữ liệu). Dòng users KHÔNG bị xoá: giữ external_subject
-- để token còn hạn không tạo lại được tài khoản qua JIT provisioning. Không còn thông tin cá nhân nào trong dòng.
--
-- ⚠️ Số V8 vì nhánh feat/budget đã giữ V7. DB local nào chạy V8 trước khi V7 merge thì Flyway báo
-- "resolved migration not applied" → `make reset` (hoặc bên merge sau đổi số file của mình).

ALTER TABLE users ADD COLUMN deletion_requested_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE users
  ADD CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED', 'DELETING', 'DELETED'));
ALTER TABLE users
  ADD CONSTRAINT ck_users_deletion_requested_at
    CHECK (status NOT IN ('DELETING', 'DELETED') OR deletion_requested_at IS NOT NULL);

-- Job hoàn tất xoá chỉ quét tài khoản đang chờ: partial index gần như rỗng.
CREATE INDEX ix_users_pending_deletion ON users (deletion_requested_at) WHERE status = 'DELETING';

-- Lúc tài khoản bên Keycloak đã bị xoá (ADR-008 A8). NULL trên một tài khoản đang/đã xoá = việc còn nợ: job
-- thử lại tới khi xong, kể cả sau nhiều giờ Keycloak lỗi. Đây là hàng đợi bền, không phụ thuộc retry của RabbitMQ.
ALTER TABLE users ADD COLUMN idp_removed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users
  ADD CONSTRAINT ck_users_idp_removed_at CHECK (idp_removed_at IS NULL OR status IN ('DELETING', 'DELETED'));
CREATE INDEX ix_users_pending_idp_removal ON users (deletion_requested_at)
  WHERE idp_removed_at IS NULL AND status IN ('DELETING', 'DELETED');

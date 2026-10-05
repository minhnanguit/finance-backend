-- Module: sync — dãy số thay đổi theo từng user, làm cursor cho pull (ADR-002 §2).
--
-- Không phải bảng dữ liệu nghiệp vụ nên không có id/version/deleted_at: mỗi user đúng một dòng,
-- dòng được tạo ở lần ghi đầu tiên (upsert trong ChangeSequencer). Row lock trên dòng này xếp hàng
-- mọi lần ghi của cùng một user, nên thứ tự số = thứ tự commit. User khác nhau không khoá nhau.
--
-- initialized_at: lần sync đầu tiên đã chạy xong (seed danh mục mặc định, ADR-002 §5). Không dùng
-- "chưa có dòng" để nhận biết, vì lần ghi nào cũng có thể tạo dòng.

CREATE TABLE user_sync_state
(
  user_id        UUID PRIMARY KEY,
  last_seq       BIGINT NOT NULL DEFAULT 0,
  initialized_at TIMESTAMP WITH TIME ZONE,
  updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT ck_user_sync_state_last_seq CHECK (last_seq >= 0)
);

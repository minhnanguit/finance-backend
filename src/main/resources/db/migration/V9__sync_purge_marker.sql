-- Module: sync — mốc dọn tombstone theo user (ADR-008 T2, T3).
--
-- Job dọn xoá thật tombstone quá hạn của user và nâng mốc này lên change_seq lớn nhất đã xoá, trong CÙNG
-- transaction. Pull với cursor thấp hơn mốc (và không mang "floor" cao hơn mốc) trả 410 sync.cursor_expired:
-- client có thể đã lỡ một lần xoá nên phải pull lại từ đầu. Mốc chỉ tăng, không bao giờ hạ.

ALTER TABLE user_sync_state ADD COLUMN purged_through_seq BIGINT NOT NULL DEFAULT 0;
ALTER TABLE user_sync_state
  ADD CONSTRAINT ck_user_sync_state_purged_through_seq CHECK (purged_through_seq >= 0);

-- Module: sync — nhật ký từng op đã xử lý, giữ 30 ngày (ADR-002 §7, ADR-006 B9).
--
-- Hai việc trong một bảng:
--   1. Chống gửi trùng: PK (user_id, op_id). Gửi lại op đã có kết quả chốt → DUPLICATE.
--      Op RETRY thì được ghi đè khi xử lý lại.
--   2. Nhật ký: máy nào, lúc nào, entity nào, kết quả gì. Không lưu data của op (B8).
--
-- Không phải bảng nghiệp vụ nên không có id/version/deleted_at; dòng quá hạn bị xoá thật bởi job dọn.

CREATE TABLE sync_ops
(
  user_id      UUID         NOT NULL,
  op_id        UUID         NOT NULL,
  device_id    VARCHAR(128) NOT NULL,
  entity       VARCHAR(30)  NOT NULL,
  entity_id    UUID         NOT NULL,
  action       VARCHAR(10)  NOT NULL,
  outcome      VARCHAR(20)  NOT NULL,
  code         VARCHAR(100),
  processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT pk_sync_ops PRIMARY KEY (user_id, op_id)
);

-- Job dọn xoá theo processed_at; tra cứu sự cố theo (user_id, processed_at).
CREATE INDEX ix_sync_ops_processed_at ON sync_ops (processed_at);
CREATE INDEX ix_sync_ops_user_processed_at ON sync_ops (user_id, processed_at);

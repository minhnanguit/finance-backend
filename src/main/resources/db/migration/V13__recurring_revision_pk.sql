-- Id phiên bản do client sinh (ADR-009 §4): khoá chính toàn cục làm một id trùng (nhân bản khoản,
-- client lỗi) thành lỗi duplicate key → op kẹt RETRY và chặn các op CREATE phụ thuộc. Id phiên bản chỉ
-- cần duy nhất trong một khoản; trong một khoản domain đã chặn trùng (recurring.invalid_field).
ALTER TABLE recurring_revisions DROP CONSTRAINT recurring_revisions_pkey;
ALTER TABLE recurring_revisions ADD CONSTRAINT pk_recurring_revisions PRIMARY KEY (recurring_id, id);

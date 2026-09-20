-- Keycloak dùng chung instance Postgres với app, nhưng database và user RIÊNG.
-- Script này chỉ chạy khi volume postgres-data còn TRỐNG (quy ước của image postgres).
-- Nếu volume đã có sẵn: chạy `make reset` (xoá sạch) hoặc `make kc-db` (tạo thủ công).
CREATE USER keycloak WITH PASSWORD 'keycloak';
CREATE DATABASE keycloak OWNER keycloak;

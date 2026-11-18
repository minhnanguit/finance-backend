#!/bin/sh
# Bản production của postgres/init/01-keycloak-db.sql: mật khẩu user `keycloak` lấy từ env KC_DB_PASSWORD
# thay vì viết cứng. Image postgres chỉ chạy script này khi volume postgres-data còn TRỐNG.
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v kc_password="$KC_DB_PASSWORD" <<-'SQL'
	CREATE USER keycloak WITH PASSWORD :'kc_password';
	CREATE DATABASE keycloak OWNER keycloak;
SQL

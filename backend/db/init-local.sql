-- 로컬 개발 DB 준비 (한 번만 실행). PostgreSQL 관리자(postgres) 계정으로 실행한다.
--   "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U postgres -f backend\db\init-local.sql
-- 아래 계정 · 비밀번호는 로컬 테스트 전용 값이다 (application-local.yml 과 같음). 운영에서 쓰지 않는다.

CREATE ROLE im010 LOGIN PASSWORD 'im010_local';
CREATE DATABASE im010 OWNER im010 ENCODING 'UTF8' TEMPLATE template0;

-- 로컬 개발 DB 준비 (한 번만 실행). 로컬 클러스터(D:\pgdata\im010, 포트 5433)의 관리자(postgres)로 실행한다.
--   "C:\Program Files\PostgreSQL\16\bin\psql.exe" -h localhost -p 5433 -U postgres -f backend\db\init-local.sql
--   관리자 비밀번호: D:\pgdata\postgres-superuser.txt (저장소 밖, 클러스터를 만들 때 생성)
-- 아래 계정 · 비밀번호는 로컬 테스트 전용 값이다 (application-local.yml 과 같음). 운영에서 쓰지 않는다.

CREATE ROLE im010 LOGIN PASSWORD 'im010_local';
CREATE DATABASE im010 OWNER im010 ENCODING 'UTF8' TEMPLATE template0;

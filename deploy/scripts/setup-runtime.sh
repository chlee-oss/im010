#!/usr/bin/env bash
# im010 운영 서버(Rocky Linux 9) 실행 환경: Java 21 · PostgreSQL 17 · im010 서비스 계정 · DB · 환경 변수 파일
#   사용: sudo bash setup-runtime.sh
# 여러 번 실행해도 된다 (이미 된 단계는 건너뜀). DB 비밀번호는 새로 만들어 /etc/im010/*.env 에만 쓴다 (화면에 출력하지 않음).
set -euo pipefail

[[ $EUID -eq 0 ]] || { echo "root 권한이 필요합니다: sudo bash $0" >&2; exit 1; }

PGVER=17
PGBIN=/usr/pgsql-$PGVER/bin
PGDATA=/var/lib/pgsql/$PGVER/data
psql_pg() { (cd /tmp && sudo -u postgres "$PGBIN/psql" -X -q -v ON_ERROR_STOP=1 "$@"); }

echo "== 1. Java 21"
rpm -q java-21-openjdk-headless >/dev/null 2>&1 || dnf -y install java-21-openjdk-headless
java -version 2>&1 | head -1

echo "== 2. PostgreSQL $PGVER (PGDG 저장소)"
if ! rpm -q pgdg-redhat-repo >/dev/null 2>&1; then
    dnf -y install "https://download.postgresql.org/pub/repos/yum/reporpms/EL-9-x86_64/pgdg-redhat-repo-latest.noarch.rpm"
fi
dnf -qy module disable postgresql
rpm -q postgresql$PGVER-server >/dev/null 2>&1 || dnf -y install postgresql$PGVER-server

if [[ ! -f $PGDATA/PG_VERSION ]]; then
    # 로컬 개발 DB 와 같은 정렬: UTF8 + ICU 한국어. 서버 안 접속만: 소켓 peer, TCP scram-sha-256
    PGSETUP_INITDB_OPTIONS="-E UTF8 --locale-provider=icu --icu-locale=ko-KR --locale=C.UTF-8 --auth-local=peer --auth-host=scram-sha-256" \
        "$PGBIN/postgresql-$PGVER-setup" initdb
fi
systemctl enable --now postgresql-$PGVER
"$PGBIN/pg_isready" -q -h /run/postgresql || { echo "PostgreSQL 이 응답하지 않습니다" >&2; exit 1; }

echo "== 3. PostgreSQL 설정 (메모리 9.4GiB 서버 · 앱 3개 약 1.4GB 와 함께)"
psql_pg <<'SQL'
ALTER SYSTEM SET listen_addresses = 'localhost';
ALTER SYSTEM SET shared_buffers = '1GB';
ALTER SYSTEM SET effective_cache_size = '4GB';
ALTER SYSTEM SET work_mem = '16MB';
ALTER SYSTEM SET maintenance_work_mem = '256MB';
ALTER SYSTEM SET max_connections = 100;
ALTER SYSTEM SET timezone = 'Asia/Seoul';
ALTER SYSTEM SET log_timezone = 'Asia/Seoul';
ALTER SYSTEM SET log_min_duration_statement = '1s';
ALTER SYSTEM SET log_connections = off;
SQL
systemctl restart postgresql-$PGVER
"$PGBIN/pg_isready" -q -h /run/postgresql

echo "== 4. im010 서비스 계정 · 디렉터리"
id im010 >/dev/null 2>&1 || useradd --system --home-dir /opt/im010 --no-create-home --shell /sbin/nologin im010
install -d -o root -g root -m 755 /opt/im010
for d in api admin batch; do install -d -o im010 -g im010 -m 750 /opt/im010/$d; done
install -d -o root -g im010 -m 750 /etc/im010

echo "== 5. DB 계정 · DB (im010)"
NEW_PASSWORD=""
if [[ "$(psql_pg -tAc "SELECT 1 FROM pg_roles WHERE rolname='im010'")" != "1" ]]; then
    NEW_PASSWORD=$(openssl rand -base64 33 | tr -d '/+=' | cut -c1-32)
    psql_pg -v pw="$NEW_PASSWORD" <<'SQL'
CREATE ROLE im010 LOGIN PASSWORD :'pw';
SQL
fi
if [[ "$(psql_pg -tAc "SELECT 1 FROM pg_database WHERE datname='im010'")" != "1" ]]; then
    psql_pg -c "CREATE DATABASE im010 OWNER im010 ENCODING 'UTF8' TEMPLATE template0"
fi

echo "== 6. 환경 변수 파일 /etc/im010/{api,admin,batch}.env (권한 640 root:im010)"
write_env() {   # $1 = 파일, 나머지 = 추가 줄
    local f=$1; shift
    if [[ -f $f ]]; then
        echo "   있음 — 그대로 둠: $f"
        return
    fi
    [[ -n $NEW_PASSWORD ]] || { echo "DB 계정은 이미 있는데 $f 가 없습니다. 비밀번호를 직접 넣어야 합니다." >&2; return 1; }
    umask 027
    {
        echo "# $(basename "$f") — setup-runtime.sh 가 만듦. 저장소에 올리지 않는다"
        echo "IM010_DB_URL=jdbc:postgresql://127.0.0.1:5432/im010"
        echo "IM010_DB_USER=im010"
        echo "IM010_DB_PASSWORD=$NEW_PASSWORD"
        printf '%s\n' "$@"
    } > "$f"
    chown root:im010 "$f"
    chmod 640 "$f"
    echo "   만듦: $f"
}
write_env /etc/im010/api.env "IM010_FRONT_BASE_URL=https://www.im010.co.kr"
write_env /etc/im010/admin.env "IM010_ADMIN_BOOTSTRAP_LOGIN_ID=admin" "# 첫 실행 전에 아래 값을 넣고, 첫 로그인(OTP · 비밀번호 변경) 후 지운다" "IM010_ADMIN_BOOTSTRAP_PASSWORD="
write_env /etc/im010/batch.env "# 알림 메일(선택): SPRING_MAIL_HOST · SPRING_MAIL_PORT · SPRING_MAIL_USERNAME · SPRING_MAIL_PASSWORD" "IM010_MAIL_FROM=im010 <noreply@im010.co.kr>"
unset NEW_PASSWORD

echo "== 7. 확인"
psql_pg -c "SELECT version()" -tA | cut -c1-60
psql_pg -tAc "SELECT datname, pg_encoding_to_char(encoding), datlocprovider, daticulocale FROM pg_database WHERE datname='im010'"
PGPASSWORD=$(grep '^IM010_DB_PASSWORD=' /etc/im010/api.env | cut -d= -f2-) \
    "$PGBIN/psql" -X -h 127.0.0.1 -U im010 -d im010 -tAc "SELECT 'im010 계정 TCP 접속 OK'"
ss -tln | grep ':5432 '
ls -l /etc/im010 /opt/im010
echo "완료"

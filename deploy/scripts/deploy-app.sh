#!/usr/bin/env bash
# im010 앱 배포 (Rocky Linux 9): jar 3개 · 화면 2개 · systemd 서비스 → api → admin → batch 순서로 (재)시작
#   사용: sudo bash deploy-app.sh [릴리스 폴더, 기본 = ~im010oper/im010-deploy/release]
# 릴리스 폴더 구성: im010-api.jar · im010-admin.jar · im010-batch.jar · web/ · admin-web/ · systemd/*.service
# 이전 jar · 화면은 /opt/im010/backup/<시각>/ 에 보관한다 (되돌릴 때 그대로 복사 후 재시작).
set -euo pipefail

[[ $EUID -eq 0 ]] || { echo "root 권한이 필요합니다: sudo bash $0" >&2; exit 1; }

REL=${1:-/home/im010oper/im010-deploy/release}
STAMP=$(date +%Y%m%d-%H%M%S)
BACKUP=/opt/im010/backup/$STAMP

for f in im010-api.jar im010-admin.jar im010-batch.jar web/index.html admin-web/index.html \
         systemd/im010-api.service systemd/im010-admin.service systemd/im010-batch.service; do
    [[ -f "$REL/$f" ]] || { echo "릴리스 파일이 없습니다: $REL/$f" >&2; exit 1; }
done
for f in api admin batch; do
    [[ -f /etc/im010/$f.env ]] || { echo "/etc/im010/$f.env 가 없습니다 — setup-runtime.sh 먼저" >&2; exit 1; }
done

wait_health() {   # $1 = 이름, $2 = 포트
    for _ in $(seq 1 60); do
        if curl -sf "http://127.0.0.1:$2/actuator/health" | grep -q '"UP"'; then
            echo "   $1 UP"
            return 0
        fi
        systemctl is-active --quiet "im010-$1" || break
        sleep 2
    done
    echo "$1 가 정상 기동하지 않았습니다 — journalctl -u im010-$1 -n 100" >&2
    journalctl -u "im010-$1" -n 40 --no-pager >&2
    return 1
}

echo "== 1. 이전 버전 보관 → $BACKUP"
install -d -m 750 "$BACKUP"
for m in api admin batch; do
    [[ -f /opt/im010/$m/im010-$m.jar ]] && cp -a "/opt/im010/$m/im010-$m.jar" "$BACKUP/"
done
for d in web admin-web; do
    [[ -d /opt/im010/$d ]] && cp -a "/opt/im010/$d" "$BACKUP/$d"
done

echo "== 2. systemd 서비스 파일"
for m in api admin batch; do
    install -m 644 "$REL/systemd/im010-$m.service" "/etc/systemd/system/im010-$m.service"
done
restorecon /etc/systemd/system/im010-*.service
systemctl daemon-reload

echo "== 3. 화면 (web · admin-web)"
for d in web admin-web; do
    rm -rf "/opt/im010/$d.new"
    cp -r "$REL/$d" "/opt/im010/$d.new"
    chown -R root:root "/opt/im010/$d.new"
    find "/opt/im010/$d.new" -type d -exec chmod 755 {} + -o -type f -exec chmod 644 {} +
    rm -rf "/opt/im010/$d.old"
    [[ -d /opt/im010/$d ]] && mv "/opt/im010/$d" "/opt/im010/$d.old"
    mv "/opt/im010/$d.new" "/opt/im010/$d"
    rm -rf "/opt/im010/$d.old"
done
restorecon -R /opt/im010/web /opt/im010/admin-web

echo "== 3-1. 알뜰AI 이미지 폴더 /opt/im010/uploads (앱이 쓰고 nginx 가 읽는다)"
install -d -o im010 -g im010 -m 755 /opt/im010/uploads
if ! semanage fcontext -l | grep -qF '/opt/im010/uploads(/.*)?'; then
    semanage fcontext -a -t httpd_sys_content_t '/opt/im010/uploads(/.*)?'
fi
restorecon -R /opt/im010/uploads

echo "== 4. api (DB 마이그레이션 Flyway 포함) → admin → batch"
for m in api admin batch; do
    install -o im010 -g im010 -m 640 "$REL/im010-$m.jar" "/opt/im010/$m/im010-$m.jar"
    systemctl enable "im010-$m" >/dev/null
    systemctl restart "im010-$m"
    case $m in
        api)   wait_health api 8080 ;;
        admin) wait_health admin 8081 ;;
        batch) sleep 10; systemctl is-active --quiet im010-batch && echo "   batch active" \
                   || { journalctl -u im010-batch -n 40 --no-pager >&2; exit 1; } ;;
    esac
done

echo "== 5. 확인"
ss -tln | awk 'NR>1{print $4}' | grep -E ':(8080|8081)$'
curl -s -o /dev/null -w "   www /api/partners → %{http_code}\n" --resolve www.im010.co.kr:443:127.0.0.1 https://www.im010.co.kr/api/partners
systemctl --no-pager --lines=0 status im010-api im010-admin im010-batch | grep -E '●|Active'
echo "완료 — 되돌리기: $BACKUP 의 파일을 제자리에 복사하고 systemctl restart im010-<모듈> (DB 마이그레이션은 되돌려지지 않음)"

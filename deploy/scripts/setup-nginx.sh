#!/usr/bin/env bash
# im010 운영 서버(Rocky Linux 9) nginx 설치 · 설정
#   사용: sudo bash setup-nginx.sh [설정 파일 폴더, 기본 = 이 스크립트 옆 ../nginx]
# 여러 번 실행해도 된다 (이미 된 단계는 건너뜀). iptables 80/443 개방은 하지 않는다 (README 운영 서버 메모).
set -euo pipefail

[[ $EUID -eq 0 ]] || { echo "root 권한이 필요합니다: sudo bash $0" >&2; exit 1; }

SRC=${1:-"$(cd "$(dirname "$0")/../nginx" && pwd)"}
STAMP=$(date +%Y%m%d-%H%M%S)
for f in nginx.conf im010.conf im010-admin.conf im010-proxy.inc im010-headers.inc im010-admin-headers.inc; do
    [[ -f "$SRC/$f" ]] || { echo "설정 파일이 없습니다: $SRC/$f" >&2; exit 1; }
done

echo "== 1. nginx 1.26 설치"
if ! rpm -q nginx >/dev/null 2>&1; then
    dnf -y module reset nginx
    dnf -y module enable nginx:1.26
    dnf -y install nginx
fi
nginx -v

echo "== 2. SELinux: 프록시(8080 · 8081) 허용, 7945 포트 등록"
setsebool -P httpd_can_network_connect 1
if ! semanage port -l | grep -E '^http_port_t\b' | grep -qw 7945; then
    semanage port -a -t http_port_t -p tcp 7945
fi

echo "== 3. 정적 파일 위치 /opt/im010/web · /opt/im010/admin-web"
mkdir -p /opt/im010/web /opt/im010/admin-web
for d in web admin-web; do
    [[ -f /opt/im010/$d/index.html ]] || echo "<!doctype html><title>im010</title><p>im010 $d — 배포 전</p>" > /opt/im010/$d/index.html
done
if ! semanage fcontext -l | grep -qF '/opt/im010/(web|admin-web)(/.*)?'; then
    semanage fcontext -a -t httpd_sys_content_t '/opt/im010/(web|admin-web)(/.*)?'
fi
restorecon -R /opt/im010
chmod 755 /opt/im010 /opt/im010/web /opt/im010/admin-web
mkdir -p /var/www/letsencrypt     # Let's Encrypt 확인 파일 (setup-letsencrypt.sh)
restorecon -R /var/www/letsencrypt

echo "== 4. 인증서 (없으면 임시 자체 서명 30일 — 실제 인증서로 바꿔야 함)"
mkdir -p /etc/pki/nginx/private
chmod 700 /etc/pki/nginx/private
for host in www.im010.co.kr admin.im010.co.kr; do
    crt=/etc/pki/nginx/$host.crt key=/etc/pki/nginx/private/$host.key
    if [[ ! -f $crt || ! -f $key ]]; then
        openssl req -x509 -nodes -newkey rsa:2048 -days 30 -subj "/CN=$host" \
            -addext "subjectAltName=DNS:$host" -keyout "$key" -out "$crt" 2>/dev/null
        chmod 600 "$key"
        echo "   임시 인증서 생성: $host"
    fi
done
restorecon -R /etc/pki/nginx

echo "== 5. 설정 파일 반영 (기존 파일은 .bak-$STAMP 로 보관)"
[[ -f /etc/nginx/nginx.conf ]] && cp -a /etc/nginx/nginx.conf "/etc/nginx/nginx.conf.bak-$STAMP"
install -m 644 "$SRC/nginx.conf" /etc/nginx/nginx.conf
for f in im010.conf im010-admin.conf im010-proxy.inc im010-headers.inc im010-admin-headers.inc; do
    [[ -f /etc/nginx/conf.d/$f ]] && cp -a "/etc/nginx/conf.d/$f" "/etc/nginx/conf.d/$f.bak-$STAMP"
    install -m 644 "$SRC/$f" "/etc/nginx/conf.d/$f"
done
restorecon -R /etc/nginx

echo "== 6. 설정 검사 · 시작"
if ! nginx -t; then
    echo "설정 오류 — 이전 파일로 되돌립니다 (실행 중인 nginx 는 그대로)" >&2
    [[ -f "/etc/nginx/nginx.conf.bak-$STAMP" ]] && cp -a "/etc/nginx/nginx.conf.bak-$STAMP" /etc/nginx/nginx.conf
    for f in im010.conf im010-admin.conf im010-proxy.inc im010-headers.inc im010-admin-headers.inc; do
        if [[ -f "/etc/nginx/conf.d/$f.bak-$STAMP" ]]; then
            cp -a "/etc/nginx/conf.d/$f.bak-$STAMP" "/etc/nginx/conf.d/$f"
        else
            rm -f "/etc/nginx/conf.d/$f"
        fi
    done
    exit 1
fi
systemctl enable nginx
if systemctl is-active --quiet nginx; then
    systemctl reload nginx
else
    systemctl start nginx
fi
systemctl --no-pager --lines=0 status nginx
ss -tlnp | grep -E ':(80|443|7945)\b' || true
echo "완료"

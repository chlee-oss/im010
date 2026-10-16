#!/usr/bin/env bash
# im010 운영 서버 Let's Encrypt 인증서 발급 · 자동 갱신 + iptables 443 개방 (Rocky Linux 9)
#   사용: sudo bash setup-letsencrypt.sh <만료 알림 메일>
# 먼저: setup-nginx.sh 실행 · DNS(www · admin → 이 서버)
#
# 80 은 외부에 닫아 둔다. Let's Encrypt 확인(HTTP-01)은 80 으로만 오므로, 발급 · 갱신하는 1분 남짓만
# iptables 에 임시 규칙(IM010_ACME_TMP, 저장하지 않음)을 넣었다가 바로 뺀다 (/usr/local/sbin/im010-acme-port80).
# 인증서 하나에 www · admin 을 함께 넣고(cert-name im010), nginx 가 보는 /etc/pki/nginx/<도메인>.crt|key 를 그 파일로 연결한다.
set -euo pipefail

[[ $EUID -eq 0 ]] || { echo "root 권한이 필요합니다: sudo bash $0 <메일>" >&2; exit 1; }
EMAIL=${1:?"인증서 만료 알림 받을 메일을 인자로 주세요"}
DOMAINS=(www.im010.co.kr admin.im010.co.kr)
WEBROOT=/var/www/letsencrypt
LIVE=/etc/letsencrypt/live/im010
STAMP=$(date +%Y%m%d-%H%M%S)

echo "== 1. DNS 확인 (이 서버 공인 IP 와 같아야 함)"
MYIP=$(ip -4 -o addr show scope global | awk '{print $4}' | cut -d/ -f1 | head -1)
for d in "${DOMAINS[@]}"; do
    got=$(getent ahostsv4 "$d" | awk 'NR==1{print $1}' || true)
    echo "   $d → ${got:-(없음)}"
    [[ "$got" == "$MYIP" ]] || { echo "DNS 가 이 서버($MYIP)를 가리키지 않습니다: $d" >&2; exit 1; }
done

echo "== 2. iptables: 443 상시 개방 (80 은 닫아 둠). 규칙 파일은 .bak-$STAMP 로 보관"
if ! iptables -C INPUT -p tcp -m tcp --dport 443 -m comment --comment IM010_HTTPS -j ACCEPT 2>/dev/null; then
    cp -a /etc/sysconfig/iptables "/etc/sysconfig/iptables.bak-$STAMP"
    pos=$(iptables -S INPUT | grep -n -- '--dport 1:59999 -j DROP' | head -1 | cut -d: -f1)
    [[ -n $pos ]] || { echo "DROP 규칙(--dport 1:59999)을 찾지 못했습니다 — 규칙을 직접 확인하세요" >&2; exit 1; }
    iptables -I INPUT $((pos - 1)) -p tcp -m tcp --dport 443 -m comment --comment IM010_HTTPS -j ACCEPT
    /usr/libexec/iptables/iptables.init save
fi
iptables -S INPUT | grep -n -E 'IM010|dport 1:59999'

echo "== 3. 80 임시 개방 도구 /usr/local/sbin/im010-acme-port80"
cat > /usr/local/sbin/im010-acme-port80 <<'EOF'
#!/bin/sh
# Let's Encrypt 발급 · 갱신 동안만 80 을 연다 (iptables 저장 안 함 — 재부팅하면 사라짐)
RULE="INPUT -p tcp -m tcp --dport 80 -m comment --comment IM010_ACME_TMP -j ACCEPT"
case "$1" in
    open)  iptables -C $RULE 2>/dev/null || iptables -I $RULE ;;
    close) while iptables -C $RULE 2>/dev/null; do iptables -D $RULE; done ;;
    *)     echo "사용: $0 open|close" >&2; exit 2 ;;
esac
EOF
chmod 755 /usr/local/sbin/im010-acme-port80
install -d /etc/letsencrypt/renewal-hooks/pre /etc/letsencrypt/renewal-hooks/post /etc/letsencrypt/renewal-hooks/deploy
printf '#!/bin/sh\nexec /usr/local/sbin/im010-acme-port80 open\n' > /etc/letsencrypt/renewal-hooks/pre/im010-open-80.sh
printf '#!/bin/sh\nexec /usr/local/sbin/im010-acme-port80 close\n' > /etc/letsencrypt/renewal-hooks/post/im010-close-80.sh
printf '#!/bin/sh\nnginx -t && systemctl reload nginx\n' > /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh
chmod 755 /etc/letsencrypt/renewal-hooks/*/*.sh

echo "== 4. certbot 설치 (EPEL)"
rpm -q epel-release >/dev/null 2>&1 || dnf -y install epel-release
rpm -q certbot >/dev/null 2>&1 || dnf -y install certbot

echo "== 5. 발급 (webroot $WEBROOT) — 이 동안만 80 임시 개방"
mkdir -p "$WEBROOT"
restorecon -R "$WEBROOT"
args=()
for d in "${DOMAINS[@]}"; do args+=(-d "$d"); done
trap '/usr/local/sbin/im010-acme-port80 close' EXIT
/usr/local/sbin/im010-acme-port80 open
certbot certonly --webroot -w "$WEBROOT" --cert-name im010 "${args[@]}" \
    --email "$EMAIL" --agree-tos --no-eff-email --non-interactive --keep-until-expiring
/usr/local/sbin/im010-acme-port80 close
trap - EXIT
iptables -S INPUT | grep -q IM010_ACME_TMP && { echo "80 임시 규칙이 남아 있습니다" >&2; exit 1; }
echo "   80 임시 규칙 제거 확인"

echo "== 6. nginx 인증서 경로 연결 (임시 자체 서명 → Let's Encrypt)"
for d in "${DOMAINS[@]}"; do
    ln -sfn "$LIVE/fullchain.pem" "/etc/pki/nginx/$d.crt"
    ln -sfn "$LIVE/privkey.pem" "/etc/pki/nginx/private/$d.key"
done
nginx -t
systemctl reload nginx

echo "== 7. 자동 갱신 타이머"
systemctl enable --now certbot-renew.timer
certbot certificates --cert-name im010
systemctl list-timers certbot-renew.timer --no-pager
echo "완료 — 갱신 시험: sudo certbot renew --dry-run (80 이 잠깐 열렸다 닫힘)"

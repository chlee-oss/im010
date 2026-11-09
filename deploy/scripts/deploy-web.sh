#!/usr/bin/env bash
# im010 고객 화면(frontend/web/dist)만 교체 — jar 재시작 없음. 알뜰AI 새 글 · 요금제 변동을 미리 렌더링 HTML 에 반영할 때 (sitemap.xml 은 api 가 만든다).
#   사용: sudo bash deploy-web.sh [화면 폴더, 기본 = ~im010oper/im010-deploy/release/web]
# 화면 폴더 = 로컬에서 `npm run build`(운영 API로 미리 렌더링)한 dist/ 를 그대로 올린 것.
# 이전 화면은 /opt/im010/backup/<시각>/web 에 보관한다.
set -euo pipefail

[[ $EUID -eq 0 ]] || { echo "root 권한이 필요합니다: sudo bash $0" >&2; exit 1; }

SRC=${1:-/home/im010oper/im010-deploy/release/web}
BACKUP=/opt/im010/backup/$(date +%Y%m%d-%H%M%S)

for f in index.html 404.html _shell.html robots.txt; do
    [[ -f "$SRC/$f" ]] || { echo "화면 파일이 없습니다: $SRC/$f (npm run build 결과인지 확인)" >&2; exit 1; }
done

install -d -m 750 "$BACKUP"
[[ -d /opt/im010/web ]] && cp -a /opt/im010/web "$BACKUP/web"

rm -rf /opt/im010/web.new /opt/im010/web.old
cp -r "$SRC" /opt/im010/web.new
chown -R root:root /opt/im010/web.new
find /opt/im010/web.new -type d -exec chmod 755 {} + -o -type f -exec chmod 644 {} +
[[ -d /opt/im010/web ]] && mv /opt/im010/web /opt/im010/web.old
mv /opt/im010/web.new /opt/im010/web
rm -rf /opt/im010/web.old
restorecon -R /opt/im010/web

for p in / /faq /sitemap.xml /robots.txt /no-such-page; do
    curl -s -o /dev/null -w "   $p → %{http_code} %{content_type}\n" --resolve www.im010.co.kr:443:127.0.0.1 "https://www.im010.co.kr$p"
done
echo "완료 — 되돌리기: sudo rm -rf /opt/im010/web && sudo cp -a $BACKUP/web /opt/im010/web && sudo restorecon -R /opt/im010/web"

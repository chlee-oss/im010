#!/usr/bin/env bash
# im010 운영 서버: 고객 화면 미리 렌더링 매일 재생성(05:00) 설치 — Node.js 22 · /opt/im010/prerender · systemd timer
#   사용: sudo bash setup-prerender.sh [올린 폴더, 기본 = 이 스크립트 옆 ..]
#   올린 폴더 구성: prerender/prerender.mjs · prerender/im010-prerender.sh · systemd/im010-prerender.service|timer
# 여러 번 실행해도 된다. prerender.mjs 가 바뀐 릴리스를 배포할 때도 다시 실행해 파일을 갈아 끼운다.
set -euo pipefail

[[ $EUID -eq 0 ]] || { echo "root 권한이 필요합니다: sudo bash $0" >&2; exit 1; }
SRC=${1:-"$(cd "$(dirname "$0")/.." && pwd)"}
for f in prerender/prerender.mjs prerender/im010-prerender.sh systemd/im010-prerender.service systemd/im010-prerender.timer; do
    [[ -f "$SRC/$f" ]] || { echo "파일이 없습니다: $SRC/$f" >&2; exit 1; }
done

echo "== 1. Node.js 22 (Rocky AppStream 모듈, 미리 렌더링 스크립트만 실행 — npm 패키지 설치 없음)"
if ! command -v node >/dev/null 2>&1; then
    dnf -y module reset nodejs
    dnf -y module enable nodejs:22
    dnf -y install nodejs
fi
node -v
major=$(node -p 'process.versions.node.split(".")[0]')
(( major >= 18 )) || { echo "node 18 이상이 필요합니다 (fetch · AbortSignal.timeout)" >&2; exit 1; }

echo "== 2. /opt/im010/prerender"
install -d -o root -g root -m 755 /opt/im010/prerender
install -o root -g root -m 644 "$SRC/prerender/prerender.mjs" /opt/im010/prerender/prerender.mjs
install -o root -g root -m 755 "$SRC/prerender/im010-prerender.sh" /opt/im010/prerender/im010-prerender.sh
restorecon -R /opt/im010/prerender || true

echo "== 3. systemd (매일 05:00)"
install -m 644 "$SRC/systemd/im010-prerender.service" /etc/systemd/system/im010-prerender.service
install -m 644 "$SRC/systemd/im010-prerender.timer" /etc/systemd/system/im010-prerender.timer
restorecon /etc/systemd/system/im010-prerender.* || true
systemctl daemon-reload
systemctl enable --now im010-prerender.timer
systemctl list-timers im010-prerender.timer --no-pager

echo "완료 — 지금 한 번 돌려 보기: sudo systemctl start im010-prerender.service && journalctl -u im010-prerender -n 30 --no-pager"

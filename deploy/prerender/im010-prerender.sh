#!/usr/bin/env bash
# im010 고객 화면 미리 렌더링 다시 만들기 (운영 서버, jar 재시작 없음) — /opt/im010/prerender/im010-prerender.sh
#   매일 05:00 im010-prerender.timer 가 실행한다. 수동 1회: sudo systemctl start im010-prerender.service
#   검사를 건너뛰고 강제로 바꿀 때만: sudo PRERENDER_FORCE=1 /opt/im010/prerender/im010-prerender.sh
#
# 1. 배포된 /opt/im010/web/_shell.html(미리 렌더링 전 틀)과 서버 안 api(127.0.0.1:8080)로
#    빈 작업 폴더에 페이지를 새로 만든다 — node 는 im010 계정 권한으로만 실행
# 2. 검사: node 실패 · 메인 없음 · 요금제 0개 · 직전보다 요금제 / 알뜰AI 글이 절반 밑으로 줄면 → 바꾸지 않고 끝 (exit 1)
# 3. 지금 web 을 복사한 새 폴더에서 미리 렌더링 결과만 갈아 끼우고(assets · robots · 인증 파일은 그대로)
#    renameat2(RENAME_EXCHANGE)로 /opt/im010/web 과 한 번에 맞바꾼다. 이전 화면은 /opt/im010/web.prev 에 남긴다
# 4. 바꾼 뒤 / · /faq 가 200 이 아니면 즉시 되돌린다
set -euo pipefail

BASE=/opt/im010
WEB=$BASE/web
PRE=$BASE/prerender
WORK=$PRE/work                 # im010 계정이 쓰는 작업 폴더
NEW=$BASE/web.prerender-new
PREV=$BASE/web.prev
API=${PRERENDER_API:-http://127.0.0.1:8080}
MIN_RATIO=${PRERENDER_MIN_RATIO:-50}   # 직전 대비 최소 % (요금제 · 글)
NODE=${NODE:-/usr/bin/node}
# 미리 렌더링이 만드는 것 — 새 폴더에서 이것만 지우고 다시 넣는다
GENERATED=(index.html 404.html _shell.html faq postpaid prepaid internet ai plans _gone terms out sitemap.xml)

log() { echo "[im010-prerender] $*"; }
fail() { log "중단: $* — 기존 화면을 그대로 둡니다"; exit 1; }

[[ $EUID -eq 0 ]] || { echo "root 권한이 필요합니다" >&2; exit 1; }
[[ -x $NODE ]] || fail "node 가 없습니다 ($NODE) — setup-prerender.sh 로 설치"
[[ -f $WEB/_shell.html ]] || fail "$WEB/_shell.html 이 없습니다 (미리 렌더링을 넣은 빌드로 배포해야 함)"

# 화면 배포(deploy-web.sh · deploy-app.sh)와 겹치지 않게
exec 9>/run/lock/im010-web.lock
flock -w 900 9 || fail "다른 화면 배포가 끝나지 않았습니다 (15분 대기)"

# 폴더가 없으면 0 (find 실패가 set -e · pipefail 로 스크립트를 끝내지 않게)
count_pages() { { find "$1" -mindepth 2 -maxdepth 2 -name index.html 2>/dev/null || true; } | wc -l; }
count_plans() { count_pages "$1/plans"; }
count_posts() { count_pages "$1/ai/posts"; }
count_gone()  { count_pages "$1/_gone/plans"; }

# ---------- 1. 만들기 ----------
rm -rf "$WORK"
install -d -o im010 -g im010 -m 750 "$WORK" "$WORK/out"
log "생성 시작 (api $API)"
if ! setpriv --reuid=im010 --regid=im010 --init-groups --no-new-privs \
        env -i PATH=/usr/bin:/bin HOME="$WORK" PRERENDER_API="$API" \
            PRERENDER_DIST="$WORK/out" PRERENDER_TEMPLATE="$WEB/_shell.html" \
        "$NODE" "$PRE/prerender.mjs"; then
    fail "prerender.mjs 실패 (api 응답 확인: curl -s $API/actuator/health)"
fi

# ---------- 2. 검사 ----------
OUT=$WORK/out
new_plans=$(count_plans "$OUT"); old_plans=$(count_plans "$WEB")
new_posts=$(count_posts "$OUT"); old_posts=$(count_posts "$WEB")
new_gone=$(count_gone "$OUT")
log "요금제 $old_plans → $new_plans · 판매 종료 $new_gone · 알뜰AI 글 $old_posts → $new_posts"
grep -q '<div id="root">' "$OUT/index.html" 2>/dev/null || fail "메인(index.html)이 만들어지지 않았습니다"
[[ -f $OUT/404.html && -f $OUT/_shell.html ]] || fail "404.html · _shell.html 이 없습니다"
if [[ ${PRERENDER_FORCE:-0} != 1 ]]; then
    (( new_plans > 0 )) || fail "게시 중 요금제가 0개입니다"
    (( new_plans * 100 >= old_plans * MIN_RATIO )) || fail "요금제가 직전($old_plans)보다 ${MIN_RATIO}% 밑으로 줄었습니다 ($new_plans)"
    (( new_posts * 100 >= old_posts * MIN_RATIO )) || fail "알뜰AI 글이 직전($old_posts)보다 ${MIN_RATIO}% 밑으로 줄었습니다 ($new_posts)"
else
    log "PRERENDER_FORCE=1 — 감소 검사를 건너뜁니다"
fi

# ---------- 3. 새 폴더 만들고 맞바꾸기 ----------
rm -rf "$NEW"
cp -a "$WEB" "$NEW"
for g in "${GENERATED[@]}"; do rm -rf "${NEW:?}/$g"; done
cp -r "$OUT/." "$NEW/"
chown -R root:root "$NEW"
find "$NEW" -type d -exec chmod 755 {} + -o -type f -exec chmod 644 {} +
chcon -R --reference="$WEB" "$NEW" 2>/dev/null || restorecon -R "$NEW" || true

exchange() {   # 두 폴더를 한 번에 맞바꾼다 (중간에 web 이 없는 순간이 없음)
    python3 - "$1" "$2" <<'PY'
import ctypes, os, sys
libc = ctypes.CDLL(None, use_errno=True)
AT_FDCWD, RENAME_EXCHANGE = -100, 2
if libc.renameat2(AT_FDCWD, sys.argv[1].encode(), AT_FDCWD, sys.argv[2].encode(), RENAME_EXCHANGE) != 0:
    e = ctypes.get_errno()
    sys.exit("renameat2: " + os.strerror(e))
PY
}

exchange "$NEW" "$WEB"           # 이제 $WEB = 새 화면, $NEW = 이전 화면
rm -rf "$PREV"
mv "$NEW" "$PREV"
log "교체 완료 (이전 화면: $PREV)"

# ---------- 4. 확인 · 실패 시 되돌리기 ----------
ok=1
for p in / /faq; do
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 --resolve www.im010.co.kr:443:127.0.0.1 "https://www.im010.co.kr$p" || true)
    log "확인 $p → $code"
    [[ $code == 200 ]] || ok=0
done
if (( ok == 0 )); then
    exchange "$PREV" "$WEB"
    log "확인 실패 — 이전 화면으로 되돌렸습니다 (실패한 결과: $PREV)"
    exit 1
fi
rm -rf "$WORK"
log "끝"

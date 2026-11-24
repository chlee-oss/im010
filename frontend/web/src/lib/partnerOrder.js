// 제휴사 노출 순서: 날마다 한 칸씩 돌린다 (회전). 타입은 partnerOrder.d.ts
// 화면(lib/partners.tsx)과 미리 렌더링(scripts/prerender.mjs)이 같은 파일을 쓴다.
//
// - 기준 순서는 API 순서(백오피스 sort_order → code). 그날의 순서는 기준 순서를 (날짜 번호 mod 제휴사 수)만큼 앞에서 돌린 것
//   → 제휴사마다 맨 앞에 서는 날이 차례로 고르게 돌아오고, 하루 동안은 같은 순서
// - 날짜는 한국 시간, 하루의 시작은 05:00 — 운영 서버가 매일 05:00 에 미리 렌더링(im010-prerender.timer)하므로,
//   자정에 바꾸면 00:00~05:00 사이 미리 렌더링된 HTML 과 화면 순서가 달라진다. 05:00 에 바꾸면 둘이 같은 날짜를 쓴다

const KST_OFFSET_MS = 9 * 60 * 60 * 1000
/** 회전이 바뀌는 시각 (한국 시간 05:00) */
const DAY_START_MS = 5 * 60 * 60 * 1000
const DAY_MS = 24 * 60 * 60 * 1000

/** 회전 날짜 번호: 한국 시간 05:00 을 하루의 시작으로 센 1970-01-01 부터의 일 수 */
export function rotationDay(now = new Date()) {
  return Math.floor((now.getTime() + KST_OFFSET_MS - DAY_START_MS) / DAY_MS)
}

/** 기준 순서 목록을 그날 순서로 돌린다. 원본은 바꾸지 않는다 */
export function rotateByDay(list, now = new Date()) {
  const n = list.length
  if (n < 2) return [...list]
  const k = ((rotationDay(now) % n) + n) % n
  return [...list.slice(k), ...list.slice(0, k)]
}

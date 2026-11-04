// 제휴사 · 협력사 페이지로 나가는 링크는 바로 /go 로 보내지 않고 이동 안내 화면(/out)을 거친다.
// /go 는 그대로 접수 기록(RC-01) · 신청 페이지 준비 중 되돌림을 맡는다.

export type OutboundKind = 'plan' | 'internet'

/** 인터넷 상품 신청처 — 현재 인터넷 상품은 모두 아정당(ajd.co.kr)에서 수집하고 신청도 아정당으로 연결한다 */
export const INTERNET_PARTNER = '아정당'

/** 이동 안내 화면에서 기다리는 시간 (ms) */
export const OUTBOUND_DELAY_MS = 1200

/** 이동 안내 화면 주소. to 는 /go/… 경로 (예: /go/26, /go/internet/3?from=MAIN) */
export const outboundPath = (kind: OutboundKind, id: number, to: string) =>
  `/out?${new URLSearchParams({ kind, id: String(id), to })}`

/** /out?to= 에는 사이트 안의 /go/ 경로만 허용한다 (다른 사이트로 보내는 열린 리다이렉트 방지) */
export function safeGoPath(to: string | null): string | null {
  if (!to || !to.startsWith('/go/') || to.startsWith('//') || to.includes('\\')) return null
  return to
}

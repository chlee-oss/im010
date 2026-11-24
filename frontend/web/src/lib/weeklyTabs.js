// im010 금주의 요금을 추천해요 — 탭별로 고르는 규칙
// 화면(components/WeeklyTabs.tsx)과 미리 렌더링(scripts/prerender.mjs)이 같은 파일을 쓴다. 타입은 weeklyTabs.d.ts
//
// 공통: 게시 중 요금제에서 제휴사마다 1개. 제휴사 순서는 랜덤.
//       제휴사 안에서는 탭의 우선순위(rank, 앞 항목이 먼저) → 월 요금이 낮은 것 → 번호가 작은 것.
//       선불은어때만 제휴사 안에서도 랜덤. 고를 요금제가 없는 탭은 숨긴다.
// "1만원대" = 10,000원 이상 20,000원 미만, "2만원대" = 20,000원 이상 30,000원 미만 …

/** QoS 문구 → Mbps ("5Mbps" → 5, "400Kbps" → 0.4, 없으면 null) */
export function qosMbps(text) {
  const m = String(text ?? '').match(/(\d+(?:\.\d+)?)\s*([MK])bps/i)
  if (!m) return null
  return m[2].toUpperCase() === 'M' ? Number(m[1]) : Number(m[1]) / 1000
}

const is5G = (p) => p.generation === '5G'
const qos = (p) => qosMbps(p.qos) ?? 0
/** 통화 무제한: 제휴사 표기 "무제한" · "기본 제공" */
const unlimitedCall = (p) => /무제한|기본\s*제공/.test(p.voice ?? '')
/** 만원대: 1만원대 → 1, 2만원대 → 2 (1만원 미만은 0) */
const manwon = (p) => Math.floor((p.monthlyPrice ?? 0) / 10000)

export const WEEKLY_TABS = [
  {
    key: 'video',
    label: '영상많이',
    icon: '▶️',
    desc: '데이터를 다 써도 빠른 속도로 계속 쓰는 후불 요금제, 제휴사마다 하나씩 골랐어요',
    type: 'POSTPAID',
    // 할인 8개월 미만(할인 없는 요금제 포함) · 소진 후 3Mbps 이상 · 1만원대 ~ 2만원대
    ok: (p) => (p.discountMonths == null || p.discountMonths < 8) && qos(p) >= 3 && [1, 2].includes(manwon(p)),
    // 5G 우선 → QoS 5Mbps 우선 → 1만원대 우선
    rank: (p) => [is5G(p) ? 0 : 1, qos(p) >= 5 ? 0 : 1, manwon(p) === 1 ? 0 : 1],
  },
  {
    key: 'long',
    label: '오래오래',
    icon: '🐢',
    desc: '할인이 24개월 넘게 이어지는 후불 요금제, 제휴사마다 하나씩 골랐어요',
    type: 'POSTPAID',
    // 할인 24개월 이상(평생할인 포함 · 할인 없는 요금제 제외) · 소진 후 3Mbps 이상 · 1만원대 ~ 3만원대
    ok: (p) => p.discountMonths != null && p.discountMonths >= 24 && qos(p) >= 3 && [1, 2, 3].includes(manwon(p)),
    // 5G 우선 → 1만원대 → 2만원대 → 3만원대
    rank: (p) => [is5G(p) ? 0 : 1, manwon(p)],
  },
  {
    key: 'call',
    label: '통화많이',
    icon: '📞',
    desc: '통화 무제한 후불 요금제 중 가장 저렴한 요금제, 제휴사마다 하나씩 골랐어요',
    type: 'POSTPAID',
    ok: (p) => unlimitedCall(p),
    // 5G 우선 → 가격 낮은 순
    rank: (p) => [is5G(p) ? 0 : 1],
  },
  {
    key: 'save',
    label: '알뜰하게',
    icon: '🐷',
    desc: '월 1,000원도 안 되는 후불 요금제, 제휴사마다 하나씩 골랐어요',
    type: 'POSTPAID',
    ok: (p) => p.monthlyPrice != null && p.monthlyPrice < 1000,
    rank: (p) => [is5G(p) ? 0 : 1],
  },
  {
    key: 'prepaid',
    label: '선불은어때',
    icon: '💰',
    desc: '약정 없이 충전해서 쓰는 선불 요금제, 제휴사마다 하나씩 골랐어요',
    type: 'PREPAID',
    ok: () => true,
    rank: null, // 제휴사 안에서도 랜덤
  },
]

const compare = (a, b) => {
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    if ((a[i] ?? 0) !== (b[i] ?? 0)) return (a[i] ?? 0) - (b[i] ?? 0)
  }
  return 0
}
const priceOf = (p) => p.monthlyPrice ?? p.chargePrice ?? 0

/** 탭 하나: 제휴사마다 1개, 제휴사 순서 랜덤 */
export function pickTab(tab, plans, random = Math.random) {
  const byPartner = new Map()
  for (const p of plans) {
    if (p.planType !== tab.type || !tab.ok(p)) continue
    byPartner.set(p.partnerCode, [...(byPartner.get(p.partnerCode) ?? []), p])
  }
  const out = [...byPartner.keys()].sort().map((code) => {
    const own = byPartner.get(code)
    if (!tab.rank) return own[Math.floor(random() * own.length)]
    return [...own].sort((a, b) => compare(tab.rank(a), tab.rank(b)) || priceOf(a) - priceOf(b) || a.id - b.id)[0]
  })
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1))
    ;[out[i], out[j]] = [out[j], out[i]]
  }
  return out
}

/** 모든 탭 (요금제가 없는 탭은 뺀다) */
export function pickWeeklyTabs(plans, random = Math.random) {
  return WEEKLY_TABS.map((tab) => ({ key: tab.key, label: tab.label, icon: tab.icon, desc: tab.desc, plans: pickTab(tab, plans, random) }))
    .filter((t) => t.plans.length > 0)
}

/** 카드 큰 글씨: "데이터 7GB" + QoS "1Mbps" → "7GB+1Mbps" */
export function headline(p) {
  if (p.unlimited) return '무제한'
  const data = String(p.dataText ?? '').replace(/^(데이터|선불)\s*/, '')
  const q = String(p.qos ?? '').match(/(\d+(?:\.\d+)?\s*[MK]bps)/i)
  return q && !/bps/i.test(data) ? `${data}+${q[1].replace(/\s+/g, '')}` : data
}

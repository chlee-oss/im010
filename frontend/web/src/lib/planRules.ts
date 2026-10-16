import type { PlanSummary } from '../api/types'

/** 데이터 소진 후 5Mbps (15Mbps · 0.5Mbps 는 아님) */
const QOS_5MBPS = /(^|[^\d.])5\s*Mbps/i

export const is5Mbps = (p: PlanSummary) => QOS_5MBPS.test(p.qos ?? '')

/**
 * 고객이 보기에 "같은 요금제": 같은 제휴사 · 같은 요금 조건(월 요금 또는 충전 금액 · 할인 기간 · 할인 후 요금 · 사용 기간).
 * 예) 토리 통화기본 100GB + 7개월 · 토리 통화기본 매일 5GB + 7개월 — 둘 다 12,100원, 7개월 후 46,200원
 */
export const samePlanKey = (p: PlanSummary) =>
  [p.partnerCode, p.planType, p.monthlyPrice, p.chargePrice, p.discountMonths, p.priceAfterDiscount, p.validDays].join('|')

/** 같은 요금제는 처음 것(목록은 가격순)만 남긴다 */
export function uniquePlans(plans: PlanSummary[]): PlanSummary[] {
  const seen = new Set<string>()
  return plans.filter((p) => {
    const key = samePlanKey(p)
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

/** Hero 카드: 게시 중인 후불 요금제 중 QoS 5Mbps 를 월 요금이 낮은 순으로 3개 (같은 요금제는 하나만) */
export function heroPicks(postpaid: PlanSummary[]): PlanSummary[] {
  return uniquePlans(postpaid.filter((p) => p.planType === 'POSTPAID' && is5Mbps(p))).slice(0, 3)
}

function shuffle<T>(list: T[], random: () => number): T[] {
  const a = [...list]
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1))
    ;[a[i], a[j]] = [a[j], a[i]]
  }
  return a
}

/** 무작위로 count 개까지. 한 제휴사에 몰리지 않게 제휴사를 돌아가며 하나씩 (제휴사 순서 · 제휴사 안 순서 모두 무작위) */
function roundRobin(plans: PlanSummary[], count: number, random: () => number): PlanSummary[] {
  const byPartner = new Map<string, PlanSummary[]>()
  for (const p of plans) {
    byPartner.set(p.partnerCode, [...(byPartner.get(p.partnerCode) ?? []), p])
  }
  const queues = shuffle([...byPartner.values()], random).map((list) => shuffle(list, random))
  const out: PlanSummary[] = []
  while (out.length < count && queues.some((q) => q.length > 0)) {
    for (const q of queues) {
      const next = q.shift()
      if (next && out.length < count) out.push(next)
    }
  }
  return out
}

/**
 * 이달의 요금제가 없을 때 대신 보여 줄 후불 요금제 count 개.
 * Hero 카드에 나온 요금제(같은 요금제 포함)는 빼고, 같은 요금제는 하나만.
 * 5G · 5Mbps → 다른 5Mbps → 나머지 후불 요금제 순서로 자리를 채운다.
 */
export function pickMonthlyFallback(
  postpaid: PlanSummary[],
  exclude: PlanSummary[] = [],
  count = 6,
  random: () => number = Math.random,
): PlanSummary[] {
  const excluded = new Set(exclude.map(samePlanKey))
  const pool = uniquePlans(postpaid.filter((p) => p.planType === 'POSTPAID' && !excluded.has(samePlanKey(p))))
  const tiers = [
    pool.filter((p) => is5Mbps(p) && p.generation === '5G'),
    pool.filter((p) => is5Mbps(p) && p.generation !== '5G'),
    pool.filter((p) => !is5Mbps(p)),
  ]
  const out: PlanSummary[] = []
  for (const tier of tiers) {
    out.push(...roundRobin(tier, count - out.length, random))
  }
  return out
}

/** 제휴사 수집 "이달의 요금제": 같은 요금제 · Hero 카드와 겹치는 요금제는 뺀다 */
export function cleanMonthly(monthly: PlanSummary[], exclude: PlanSummary[] = []): PlanSummary[] {
  const excluded = new Set(exclude.map(samePlanKey))
  return uniquePlans(monthly.filter((p) => !excluded.has(samePlanKey(p))))
}

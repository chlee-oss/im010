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

/** 제휴사마다 한 묶음 (처음 나온 순서) */
function byPartner(plans: PlanSummary[]): PlanSummary[][] {
  const groups = new Map<string, PlanSummary[]>()
  for (const p of plans) {
    groups.set(p.partnerCode, [...(groups.get(p.partnerCode) ?? []), p])
  }
  return [...groups.values()]
}

/**
 * 이달의 요금제가 없을 때 대신 보여 줄 후불 요금제 — 제휴사마다 1개 (제휴사 순서는 무작위).
 * Hero 카드에 나온 요금제(같은 요금제 포함)는 빼고, 제휴사 안에서는 5G · 5Mbps → 다른 5Mbps → 나머지 순으로 무작위 1개.
 */
export function pickMonthlyFallback(
  postpaid: PlanSummary[],
  exclude: PlanSummary[] = [],
  random: () => number = Math.random,
): PlanSummary[] {
  const excluded = new Set(exclude.map(samePlanKey))
  const pool = uniquePlans(postpaid.filter((p) => p.planType === 'POSTPAID' && !excluded.has(samePlanKey(p))))
  return shuffle(byPartner(pool), random).map((own) => {
    const tier =
      [own.filter((p) => is5Mbps(p) && p.generation === '5G'), own.filter((p) => is5Mbps(p)), own].find((t) => t.length > 0) ?? own
    return tier[Math.floor(random() * tier.length)]
  })
}

/** 제휴사 수집 "이달의 요금제": Hero 카드와 겹치는 요금제 · 같은 요금제는 빼고, 제휴사마다 1개 (사이트 순서 첫 번째) */
export function cleanMonthly(monthly: PlanSummary[], exclude: PlanSummary[] = []): PlanSummary[] {
  const excluded = new Set(exclude.map(samePlanKey))
  return byPartner(uniquePlans(monthly.filter((p) => !excluded.has(samePlanKey(p))))).map((own) => own[0])
}

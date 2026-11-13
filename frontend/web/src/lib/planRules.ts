import type { PlanSummary } from '../api/types'

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

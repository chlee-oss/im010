import type { PlanSummary } from '../api/types'

// 요금제분석 — 게시 중인 후불 요금제(고객 API /api/plans?type=POSTPAID)로 바로 계산한다. 예시 값 없음.

/** "매일 2GB" 처럼 하루 단위로 주는 데이터 (GB) */
const dailyGb = (p: PlanSummary): number | null => {
  const m = p.dataText.match(/매일\s*([\d.]+)\s*GB/i)
  return m ? Number(m[1]) : null
}

/** 한 달 기준 데이터 양 (GB): 매일 데이터는 30일로 더한다. 무제한 · 데이터 미상은 null */
export function monthlyGb(p: PlanSummary): number | null {
  if (p.unlimited || p.dataGb == null) return null
  const daily = dailyGb(p)
  return Number(p.dataGb) + (daily ? daily * 30 : 0)
}

export interface GbPrice {
  partner: string
  value: number
  plans: number
}

/**
 * 제휴사별 1GB 환산 평균 월 요금 (낮은 순).
 * 대상: 기본 데이터 1GB 이상 · 무제한 아님 · 매일 데이터 없음 (하루 단위 데이터는 한 달 총량으로 바꾸면 비교가 왜곡돼 뺀다).
 * 요금은 게시된 월 요금(할인 기간이 있으면 할인 중 요금).
 */
export function gbPriceByPartner(postpaid: PlanSummary[]): GbPrice[] {
  const acc = new Map<string, { sum: number; n: number }>()
  for (const p of postpaid) {
    if (p.planType !== 'POSTPAID' || p.unlimited || p.monthlyPrice == null || p.dataGb == null) continue
    const gb = Number(p.dataGb)
    if (gb < 1 || dailyGb(p) != null) continue
    const a = acc.get(p.partnerName) ?? { sum: 0, n: 0 }
    a.sum += p.monthlyPrice / gb
    a.n += 1
    acc.set(p.partnerName, a)
  }
  return [...acc]
    .map(([partner, a]) => ({ partner, value: Math.round(a.sum / a.n), plans: a.n }))
    .sort((x, y) => x.value - y.value)
}

const BUCKETS = [
  { label: '3GB 이하', test: (gb: number | null) => gb != null && gb <= 3 },
  { label: '3GB 초과 ~ 10GB', test: (gb: number | null) => gb != null && gb > 3 && gb <= 10 },
  { label: '10GB 초과 ~ 30GB', test: (gb: number | null) => gb != null && gb > 10 && gb <= 30 },
  { label: '30GB 초과 · 무제한', test: (gb: number | null) => gb == null || gb > 30 },
]

/** 게시 요금제가 가장 많은 데이터 구간 (한 달 기준 데이터 양) */
export function topDataBucket(postpaid: PlanSummary[]): { label: string; count: number } | null {
  const plans = postpaid.filter((p) => p.planType === 'POSTPAID' && (p.unlimited || p.dataGb != null))
  if (plans.length === 0) return null
  return BUCKETS.map((b) => ({ label: b.label, count: plans.filter((p) => b.test(monthlyGb(p))).length })).reduce((best, b) =>
    b.count > best.count ? b : best,
  )
}

/** 게시 요금제 중 월 요금이 가장 낮은 요금제 */
export function cheapestPlan(postpaid: PlanSummary[]): PlanSummary | null {
  return postpaid
    .filter((p) => p.planType === 'POSTPAID' && p.monthlyPrice != null)
    .reduce<PlanSummary | null>((min, p) => (min == null || p.monthlyPrice! < min.monthlyPrice! ? p : min), null)
}

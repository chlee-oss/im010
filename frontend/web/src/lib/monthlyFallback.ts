import type { PlanSummary } from '../api/types'

/** 이달의 요금제가 없을 때 대신 보여 줄 요금제 조건: 후불 · 5G · 데이터 소진 후 5Mbps */
const QOS_5MBPS = /(^|[^\d.])5\s*Mbps/i

export const isFallbackCandidate = (p: PlanSummary) =>
  p.planType === 'POSTPAID' && p.generation === '5G' && QOS_5MBPS.test(p.qos ?? '')

function shuffle<T>(list: T[], random: () => number): T[] {
  const a = [...list]
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1))
    ;[a[i], a[j]] = [a[j], a[i]]
  }
  return a
}

/**
 * 조건에 맞는 요금제에서 무작위로 count 개. 한 제휴사에 몰리지 않게 제휴사를 돌아가며 하나씩 뽑는다
 * (제휴사 순서 · 제휴사 안의 순서 모두 무작위).
 */
export function pickMonthlyFallback(plans: PlanSummary[], count = 6, random: () => number = Math.random): PlanSummary[] {
  const byPartner = new Map<string, PlanSummary[]>()
  for (const p of plans.filter(isFallbackCandidate)) {
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

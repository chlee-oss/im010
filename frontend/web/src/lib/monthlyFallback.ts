import type { PlanSummary } from '../api/types'

/**
 * 이달의 요금제가 없을 때 대신 보여 줄 요금제 조건: 후불 · 데이터 소진 후 5Mbps.
 * 5G 요금제를 먼저 뽑고, 모자라면 다른 세대(LTE)의 5Mbps 요금제로 채운다.
 */
const QOS_5MBPS = /(^|[^\d.])5\s*Mbps/i

export const isFallbackCandidate = (p: PlanSummary) => p.planType === 'POSTPAID' && QOS_5MBPS.test(p.qos ?? '')

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

/** 5G · 5Mbps 에서 먼저 count 개까지, 모자란 자리는 나머지 5Mbps 요금제에서 */
export function pickMonthlyFallback(plans: PlanSummary[], count = 6, random: () => number = Math.random): PlanSummary[] {
  const candidates = plans.filter(isFallbackCandidate)
  const first = roundRobin(
    candidates.filter((p) => p.generation === '5G'),
    count,
    random,
  )
  const rest = roundRobin(
    candidates.filter((p) => p.generation !== '5G'),
    count - first.length,
    random,
  )
  return [...first, ...rest]
}

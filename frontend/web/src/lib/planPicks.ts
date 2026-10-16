import type { PlanSummary } from '../api/types'
import { samePlanKey, uniquePlans } from './planRules'

/**
 * 제휴사 한 곳에서 메인에 보여 줄 2개: 백오피스에서 지정한 1 · 2번째 자리를 먼저 채우고,
 * 빈 자리는 나머지 요금제 중 월 요금이 낮은 순 (목록은 이미 가격순). 같은 요금제(lib/planRules)는 두 번 넣지 않는다.
 */
export function pickTwo(plans: PlanSummary[]): PlanSummary[] {
  const slots: (PlanSummary | undefined)[] = [plans.find((p) => p.mainPick === 1), plans.find((p) => p.mainPick === 2)]
  if (slots[0] && slots[1] && samePlanKey(slots[0]) === samePlanKey(slots[1])) slots[1] = undefined
  const taken = new Set(slots.filter((s): s is PlanSummary => !!s).map(samePlanKey))
  const rest = uniquePlans(plans.filter((p) => !slots.includes(p) && !taken.has(samePlanKey(p))))
  return slots.map((s) => s ?? rest.shift()).filter((p): p is PlanSummary => !!p)
}

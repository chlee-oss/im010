import type { PlanSummary } from '../api/types'

/**
 * 제휴사 한 곳에서 메인에 보여 줄 2개: 백오피스에서 지정한 1 · 2번째 자리를 먼저 채우고,
 * 빈 자리는 나머지 요금제 중 월 요금이 낮은 순 (목록은 이미 가격순).
 */
export function pickTwo(plans: PlanSummary[]): PlanSummary[] {
  const slots: (PlanSummary | undefined)[] = [plans.find((p) => p.mainPick === 1), plans.find((p) => p.mainPick === 2)]
  const rest = plans.filter((p) => !slots.includes(p))
  return slots.map((s) => s ?? rest.shift()).filter((p): p is PlanSummary => !!p)
}

import type { PlanSummary } from '../api/types'

export interface WeeklyTab {
  key: 'video' | 'long' | 'call' | 'save' | 'prepaid'
  label: string
  /** 탭 아이콘 (이모지) */
  icon: string
  /** 탭 아래 한 줄 설명 */
  desc: string
  plans: PlanSummary[]
}

export function qosMbps(text: string | null | undefined): number | null
export function pickWeeklyTabs(plans: PlanSummary[], random?: () => number): WeeklyTab[]
export function headline(p: PlanSummary): string

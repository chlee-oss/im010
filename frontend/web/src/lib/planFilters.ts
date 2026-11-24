import type { PlanSummary } from '../api/types'
import { qosMbps } from './weeklyTabs'

/**
 * 조건으로 찾고, 제휴사별로 비교해요 필터 (components/PlanFinder).
 * 한 그룹 안에서는 여러 개를 고를 수 있고(하나라도 맞으면 통과), 그룹끼리는 모두 맞아야 한다.
 * single 그룹(월 요금)은 하나만 고른다.
 */
export interface FilterOption {
  v: string
  t: string
  test: (p: PlanSummary) => boolean
}
export interface FilterGroup {
  key: string
  label: string
  hint?: string
  single?: boolean
  opts: FilterOption[]
}

const qos = (p: PlanSummary) => qosMbps(p.qos) ?? 0
export const unlimitedCall = (p: PlanSummary) => /무제한|기본\s*제공/.test(p.voice ?? '')
const price = (p: PlanSummary) => p.monthlyPrice ?? 0

export const FILTER_GROUPS: FilterGroup[] = [
  {
    key: 'net',
    label: '통신망',
    opts: [
      { v: 'SKT', t: 'SKT', test: (p) => p.network === 'SKT' },
      { v: 'KT', t: 'KT', test: (p) => p.network === 'KT' },
      { v: 'LGU', t: 'LGU+', test: (p) => p.network === 'LGU' },
    ],
  },
  {
    key: 'data',
    label: '데이터',
    hint: '월 기본 제공량',
    opts: [
      { v: '10', t: '~10GB', test: (p) => !p.unlimited && p.dataGb != null && p.dataGb <= 10 },
      { v: '50', t: '10~50GB', test: (p) => !p.unlimited && p.dataGb != null && p.dataGb > 10 && p.dataGb <= 50 },
      { v: '100', t: '50~100GB', test: (p) => !p.unlimited && p.dataGb != null && p.dataGb > 50 && p.dataGb <= 100 },
      { v: 'max', t: '100GB~무제한', test: (p) => p.unlimited || (p.dataGb ?? 0) > 100 },
    ],
  },
  {
    key: 'qos',
    label: 'QoS',
    hint: '데이터를 다 쓴 뒤 속도',
    opts: [
      { v: '0', t: '없음', test: (p) => qos(p) === 0 },
      { v: '1', t: '~1Mbps', test: (p) => qos(p) > 0 && qos(p) <= 1 },
      { v: '3', t: '~3Mbps', test: (p) => qos(p) > 1 && qos(p) <= 3 },
      { v: '5', t: '~5Mbps', test: (p) => qos(p) > 3 && qos(p) < 5 },
      { v: '5+', t: '5Mbps 이상', test: (p) => qos(p) >= 5 },
    ],
  },
  {
    key: 'voice',
    label: '음성',
    opts: [
      { v: 'limit', t: '제한', test: (p) => !unlimitedCall(p) },
      { v: 'unl', t: '무제한', test: (p) => unlimitedCall(p) },
    ],
  },
  {
    key: 'disc',
    label: '할인기간',
    opts: [
      { v: 'none', t: '할인 없음', test: (p) => !p.discountMonths },
      { v: '10', t: '10개월 이하', test: (p) => !!p.discountMonths && p.discountMonths <= 10 },
      { v: '24', t: '24개월 이하', test: (p) => !!p.discountMonths && p.discountMonths > 10 && p.discountMonths <= 24 },
      { v: 'life', t: '평생', test: (p) => (p.discountMonths ?? 0) > 24 },
    ],
  },
  {
    key: 'price',
    label: '월 요금',
    hint: '하나만 골라요',
    single: true,
    opts: [
      { v: '1000', t: '1,000원 이하', test: (p) => price(p) <= 1000 },
      { v: '10000', t: '1만원 이하', test: (p) => price(p) <= 10000 },
      { v: '20000', t: '2만원 이하', test: (p) => price(p) <= 20000 },
      { v: '30000', t: '3만원 이하', test: (p) => price(p) <= 30000 },
      { v: '50000', t: '5만원 이하', test: (p) => price(p) <= 50000 },
      { v: 'over', t: '5만원 초과', test: (p) => price(p) > 50000 },
    ],
  },
]

/** 자주 찾는 조건 (한 번에 켜고 끄는 바로가기) */
export const QUICK_FILTERS: { key: string; v: string; t: string }[] = [
  { key: 'voice', v: 'unl', t: '통화 무제한' },
  { key: 'qos', v: '5+', t: '다 써도 5Mbps' },
  { key: 'price', v: '20000', t: '월 2만원 이하' },
  { key: 'data', v: 'max', t: '데이터 100GB 이상' },
  { key: 'disc', v: '24', t: '할인 24개월' },
]

export type Selection = Record<string, string[]>

export function passGroup(p: PlanSummary, g: FilterGroup, picked: string[] | undefined): boolean {
  if (!picked || picked.length === 0) return true
  return g.opts.some((o) => picked.includes(o.v) && o.test(p))
}

/** skip: 칩 옆 숫자를 셀 때 그 그룹 조건은 빼고 센다 */
export function matches(p: PlanSummary, groups: FilterGroup[], sel: Selection, skip?: string): boolean {
  return groups.every((g) => g.key === skip || passGroup(p, g, sel[g.key]))
}

export const SORTS = {
  price: { label: '월 요금 낮은 순', cmp: (a: PlanSummary, b: PlanSummary) => price(a) - price(b) || after(a) - after(b) || a.id - b.id },
  after: { label: '할인 후 요금 낮은 순', cmp: (a: PlanSummary, b: PlanSummary) => after(a) - after(b) || price(a) - price(b) || a.id - b.id },
  data: {
    label: '데이터 많은 순',
    cmp: (a: PlanSummary, b: PlanSummary) => dataAmount(b) - dataAmount(a) || price(a) - price(b) || a.id - b.id,
  },
} as const
export type SortKey = keyof typeof SORTS

function after(p: PlanSummary) {
  return p.discountMonths && p.priceAfterDiscount != null ? p.priceAfterDiscount : price(p)
}
function dataAmount(p: PlanSummary) {
  return p.unlimited ? 1e9 : (p.dataGb ?? 0)
}

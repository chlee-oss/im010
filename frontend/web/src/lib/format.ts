import type { PlanSummary } from '../api/types'

export const won = (n: number | null | undefined): string => (n == null ? '-' : n.toLocaleString('ko-KR'))

/** 후불은 월 요금, 선불은 충전 금액. */
export const planPrice = (p: PlanSummary): number | null => (p.planType === 'POSTPAID' ? p.monthlyPrice : p.chargePrice)

export const planPriceUnit = (p: PlanSummary): string => (p.planType === 'POSTPAID' ? '원 / 월' : '원')

/** "2026-10-01" → "2026.10.01" */
export const dotDate = (iso: string | null | undefined): string => (iso ? iso.replaceAll('-', '.') : '')

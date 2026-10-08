import { createContext, useContext, type ReactNode } from 'react'
import { api } from '../api/client'
import type { Partner } from '../api/types'
import { useApi } from './useApi'

const PartnerContext = createContext<Map<string, Partner>>(new Map())

/** 제휴사 목록(브랜드명 · 칩 색상)을 한 번만 불러와 화면 전체에서 쓴다. */
export function PartnerProvider({ children }: { children: ReactNode }) {
  const { data } = useApi((signal) => api.partners(signal), [])
  const map = new Map((data ?? []).map((p) => [p.code, p]))
  return <PartnerContext.Provider value={map}>{children}</PartnerContext.Provider>
}

export function usePartners(): Map<string, Partner> {
  return useContext(PartnerContext)
}

/** 브랜드 칩. 색상은 백오피스(제휴사관리)에 등록된 값. */
export function BrandChip({ code, name, className = '' }: { code: string; name?: string; className?: string }) {
  const partner = usePartners().get(code)
  const style = partner ? { background: partner.chipBg, color: partner.chipFg } : undefined
  return (
    <span className={`brand-chip ${code} ${className}`.trim()} style={style}>
      {partner?.name ?? name ?? code}
    </span>
  )
}

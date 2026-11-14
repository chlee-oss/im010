import { createContext, useContext, type ReactNode } from 'react'
import { api } from '../api/client'
import type { Partner } from '../api/types'
import { useApi } from './useApi'

const PartnerContext = createContext<Map<string, Partner>>(new Map())

/** 제휴사 목록(브랜드명 · 칩 색상)을 한 번만 불러와 화면 전체에서 쓴다. */
export function PartnerProvider({ children }: { children: ReactNode }) {
  const { data, loading } = useApi((signal) => api.partners(signal), [])
  const map = new Map((data ?? []).map((p) => [p.code, p]))
  return (
    <PartnerContext.Provider value={map}>
      {/* 제휴사가 늦게 오면 메인 소개 줄 · 탭이 나중에 생겨 높이가 바뀐다 — 섹션 주소 이동(lib/scrollHold)이 기다리도록 표시 (화면엔 안 보임) */}
      {loading && <span hidden aria-busy="true" />}
      {children}
    </PartnerContext.Provider>
  )
}

export function usePartners(): Map<string, Partner> {
  return useContext(PartnerContext)
}

/** 브랜드 칩. 색상 · 로고는 백오피스(제휴사관리)에 등록된 값 — 로고가 있으면 로고 이미지를 보여 준다. */
export function BrandChip({ code, name, className = '' }: { code: string; name?: string; className?: string }) {
  const partner = usePartners().get(code)
  // 로고가 등록돼 있으면 칩 자리에 로고 (같은 brand-chip 자리 · 여백 규칙을 따른다)
  if (partner?.logoUrl) {
    return <img className={`brand-chip brand-logo ${className}`.trim()} src={partner.logoUrl} alt={partner.name} loading="lazy" />
  }
  const style = partner ? { background: partner.chipBg, color: partner.chipFg } : undefined
  return (
    <span className={`brand-chip ${code} ${className}`.trim()} style={style}>
      {partner?.name ?? name ?? code}
    </span>
  )
}

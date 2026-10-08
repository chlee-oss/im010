import { useEffect, useState } from 'react'
import { get } from './api'
import { useMe } from './me'
import type { Partner } from './types'

/** 필터용 제휴사 목록 (제휴사관리 조회 권한이 있으면 전체, 없으면 빈 목록) */
export function usePartnerOptions(): [string, string][] {
  const { can, me } = useMe()
  const [list, setList] = useState<[string, string][]>([])
  const allowed = can('PA-01', 'VIEW') || can('BA-01', 'VIEW')
  useEffect(() => {
    if (!me || !allowed) return
    get<Partner[]>(can('PA-01', 'VIEW') ? '/partners' : '/schedules')
      .then((ps) => setList(ps.map((p) => [p.code, p.name])))
      .catch(() => setList([]))
  }, [me, allowed, can])
  return list
}

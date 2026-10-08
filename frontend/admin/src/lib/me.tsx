import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { get } from './api'
import type { Action, Me } from './types'

interface MeState {
  me: Me | null
  reload: () => Promise<void>
  can: (program: string, action: Action) => boolean
}

const MeContext = createContext<MeState>({ me: null, reload: async () => {}, can: () => false })

/** 로그인한 관리자 · 메뉴 · 처리 건수 배지. 권한은 서버가 다시 확인하고, 화면은 버튼을 숨기는 데만 쓴다. */
export function MeProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null)
  const reload = useCallback(async () => {
    setMe(await get<Me>('/me'))
  }, [])
  useEffect(() => {
    void reload()
  }, [reload])
  const can = useCallback(
    (program: string, action: Action) =>
      !!me?.menus.some((g) => g.items.some((i) => i.id === program && i.actions.includes(action))),
    [me],
  )
  return <MeContext.Provider value={{ me, reload, can }}>{children}</MeContext.Provider>
}

export const useMe = () => useContext(MeContext)

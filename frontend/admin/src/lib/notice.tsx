import { createContext, useCallback, useContext, useState, type ReactNode } from 'react'
import type { BulkResult } from './types'

type Kind = 'ok' | 'error'
interface Notice {
  id: number
  kind: Kind
  text: string
}

const NoticeContext = createContext<(kind: Kind, text: string) => void>(() => {})

/** 화면 오른쪽 위 알림 (처리 결과 · 오류) */
export function NoticeProvider({ children }: { children: ReactNode }) {
  const [list, setList] = useState<Notice[]>([])
  const notify = useCallback((kind: Kind, text: string) => {
    const id = Date.now() + Math.random()
    setList((l) => [...l, { id, kind, text }])
    setTimeout(() => setList((l) => l.filter((n) => n.id !== id)), kind === 'error' ? 7000 : 3500)
  }, [])
  return (
    <NoticeContext.Provider value={notify}>
      {children}
      <div className="notices" role="status" aria-live="polite">
        {list.map((n) => (
          <div key={n.id} className={'notice ' + n.kind}>
            {n.text}
          </div>
        ))}
      </div>
    </NoticeContext.Provider>
  )
}

export const useNotify = () => useContext(NoticeContext)

/** 여러 건 처리 결과 문장 */
export function bulkText(verb: string, r: BulkResult): string {
  const skipped = r.skipped.length
    ? ` · ${r.skipped.length}건 제외 — ` + r.skipped.slice(0, 3).map((s) => `#${s.id} ${s.reason}`).join(', ') + (r.skipped.length > 3 ? ' …' : '')
    : ''
  return `${r.done.length}건 ${verb}${skipped}`
}

/** 실행하고 결과 · 오류를 알림으로 보여 준다 */
export function useRun() {
  const notify = useNotify()
  const [busy, setBusy] = useState(false)
  const run = useCallback(
    async <T,>(task: () => Promise<T>, message?: (r: T) => string): Promise<T | undefined> => {
      setBusy(true)
      try {
        const r = await task()
        if (message) notify('ok', message(r))
        return r
      } catch (e) {
        notify('error', e instanceof Error ? e.message : String(e))
        return undefined
      } finally {
        setBusy(false)
      }
    },
    [notify],
  )
  return { run, busy }
}

import { useEffect, useRef, type ReactNode } from 'react'

export function PageHead({ title, children }: { title: string; children?: ReactNode }) {
  useEffect(() => {
    document.title = `${title} · im010 admin`
  }, [title])
  return (
    <div className="page-head">
      <h1>{title}</h1>
      <div className="page-actions">{children}</div>
    </div>
  )
}

export function Tabs<T extends string>({
  value,
  onChange,
  tabs,
}: {
  value: T
  onChange: (v: T) => void
  tabs: [T, ReactNode][]
}) {
  return (
    <div className="tabs" role="tablist">
      {tabs.map(([key, label]) => (
        <button key={key} role="tab" aria-selected={value === key} className={value === key ? 'on' : ''} onClick={() => onChange(key)}>
          {label}
        </button>
      ))}
    </div>
  )
}

export function Badge({ tone, children }: { tone?: 'coral' | 'green' | 'gray' | 'amber' | 'red' | 'navy'; children: ReactNode }) {
  return <span className={'badge ' + (tone ?? 'gray')}>{children}</span>
}

export function Loading({ error }: { error?: string | null }) {
  return <div className="state">{error ? '⚠ ' + error : '불러오는 중…'}</div>
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="state">{children}</div>
}

/** 오른쪽에서 열리는 상세 패널 */
export function Drawer({ title, onClose, children }: { title: ReactNode; onClose: () => void; children: ReactNode }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])
  return (
    <div className="drawer-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <aside className="drawer" role="dialog" aria-modal="true">
        <div className="drawer-head">
          <h2>{title}</h2>
          <button className="icon" onClick={onClose} aria-label="닫기">
            ✕
          </button>
        </div>
        <div className="drawer-body">{children}</div>
      </aside>
    </div>
  )
}

/** 확인 · 입력 창 */
export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    ref.current?.querySelector<HTMLElement>('input, textarea, select, button')?.focus()
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])
  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title} ref={ref}>
        <h2>{title}</h2>
        {children}
      </div>
    </div>
  )
}

/** 체크박스 선택 상태 */
export function selectAll<T extends { id: number }>(rows: T[], selected: Set<number>): boolean {
  return rows.length > 0 && rows.every((r) => selected.has(r.id))
}

export function Pager({ page, total, size = 50, onChange }: { page: number; total: number; size?: number; onChange: (p: number) => void }) {
  const last = Math.max(1, Math.ceil(total / size))
  if (last <= 1) return null
  return (
    <div className="pager">
      <button disabled={page <= 1} onClick={() => onChange(page - 1)}>
        ‹ 이전
      </button>
      <span>
        {page} / {last} 페이지 · {total.toLocaleString()}건
      </span>
      <button disabled={page >= last} onClick={() => onChange(page + 1)}>
        다음 ›
      </button>
    </div>
  )
}

import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Loading, Tabs } from '../components/ui'
import { get, qs } from '../lib/api'
import { addDays, dateTime, today } from '../lib/format'
import type { CalendarRow } from '../lib/types'
import { useLoad } from '../lib/useLoad'

const WEEKDAYS = ['월', '화', '수', '목', '금', '토', '일']

/** 서울 날짜(yyyy-MM-dd) */
function seoulDate(iso: string): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(new Date(iso))
}

function weekday(date: string): number {
  return (new Date(date + 'T00:00:00Z').getUTCDay() + 6) % 7 // 월 = 0
}

/** PR-01 [게시 일정]: 주간 · 월간 달력. 지난 날짜는 게시 결과, 앞으로는 예약 건. 건을 누르면 요금제 상세 */
export default function PlanCalendar() {
  const navigate = useNavigate()
  const [mode, setMode] = useState<'WEEK' | 'MONTH'>('WEEK')
  const [anchor, setAnchor] = useState(today())

  const monday = addDays(anchor, -weekday(anchor))
  const first = anchor.slice(0, 8) + '01'
  const start = mode === 'WEEK' ? monday : addDays(first, -weekday(first))
  const days = mode === 'WEEK' ? 7 : 42
  const end = addDays(start, days - 1)
  const { data, error } = useLoad(() => get<CalendarRow[]>('/plans/calendar' + qs({ from: start, to: end })), [start, end])

  const byDay = new Map<string, CalendarRow[]>()
  for (const r of data ?? []) {
    const d = seoulDate(r.publishedAt ?? r.publishAt!)
    byDay.set(d, [...(byDay.get(d) ?? []), r])
  }
  const t = today()
  const move = (dir: number) => setAnchor(mode === 'WEEK' ? addDays(anchor, dir * 7) : (() => {
    const d = new Date(first + 'T00:00:00Z')
    d.setUTCMonth(d.getUTCMonth() + dir)
    return d.toISOString().slice(0, 10)
  })())

  return (
    <>
      <div className="filter-row">
        <Tabs value={mode} onChange={setMode} tabs={[['WEEK', '주간'], ['MONTH', '월간']]} />
        <button onClick={() => move(-1)}>‹</button>
        <b>{mode === 'WEEK' ? `${start.replaceAll('-', '.')} ~ ${end.slice(5).replace('-', '.')}` : `${anchor.slice(0, 4)}년 ${Number(anchor.slice(5, 7))}월`}</b>
        <button onClick={() => move(1)}>›</button>
        <button onClick={() => setAnchor(today())}>오늘</button>
        <span className="muted">✓ 게시 완료 · 📅 게시 예약</span>
      </div>
      {!data ? (
        <Loading error={error} />
      ) : (
        <div className={'calendar ' + mode.toLowerCase()}>
          {WEEKDAYS.map((w) => (
            <div key={w} className="cal-head">
              {w}
            </div>
          ))}
          {Array.from({ length: days }, (_, i) => addDays(start, i)).map((d) => {
            const rows = byDay.get(d) ?? []
            const other = mode === 'MONTH' && d.slice(0, 7) !== anchor.slice(0, 7)
            return (
              <div key={d} className={'cal-day' + (d === t ? ' today' : '') + (other ? ' other' : '')}>
                <div className="cal-date">{Number(d.slice(8))}</div>
                {rows.slice(0, mode === 'WEEK' ? 30 : 4).map((r) => (
                  <button key={r.versionId} className={'cal-item ' + (r.publishedAt ? 'done' : 'scheduled')} onClick={() => navigate(`/plans/${r.planId}`)} title={`${r.partnerName} ${r.name} v${r.versionNo}`}>
                    {r.publishedAt ? '✓' : '📅'} {dateTime(r.publishedAt ?? r.publishAt).slice(6)} {r.partnerName} · {r.name}
                  </button>
                ))}
                {mode === 'MONTH' && rows.length > 4 && <div className="muted small">외 {rows.length - 4}건</div>}
              </div>
            )
          })}
        </div>
      )}
    </>
  )
}

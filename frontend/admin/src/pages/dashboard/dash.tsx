import { useState, type ReactNode } from 'react'
import { Badge } from '../../components/ui'
import { put, qs } from '../../lib/api'
import { INTERNET_CARRIER, NETWORK, addDays, fullDateTime, today } from '../../lib/format'
import { useMe } from '../../lib/me'
import { useRun } from '../../lib/notice'
import type { DashInterest, DashSettings } from '../../lib/types'

// ---------- 조회 조건 ----------

export type Device = 'ALL' | 'PC' | 'MO'

export interface DashQuery {
  from: string
  to: string
  device: Device
}

const PRESETS: [string, number][] = [
  ['오늘', 0],
  ['7일', 6],
  ['30일', 29],
  ['90일', 89],
]

/** 기간 · 기기 조건 (화면을 옮겨도 유지) */
export function useDashQuery() {
  const [q, setQ] = useState<DashQuery>(() => {
    try {
      const saved = JSON.parse(sessionStorage.getItem('im010.dashQuery') ?? 'null') as DashQuery | null
      if (saved?.from && saved.to) return saved
    } catch {
      /* 저장값 없음 */
    }
    return { from: addDays(today(), -6), to: today(), device: 'ALL' }
  })
  const update = (next: DashQuery) => {
    setQ(next)
    try {
      sessionStorage.setItem('im010.dashQuery', JSON.stringify(next))
    } catch {
      /* 저장 못 해도 화면은 동작 */
    }
  }
  return { q, setQ: update, params: qs({ from: q.from, to: q.to, device: q.device }) }
}

export function DashFilter({ q, onChange, csv }: { q: DashQuery; onChange: (q: DashQuery) => void; csv?: string }) {
  const t = today()
  return (
    <div className="filter-row dash-filter">
      <div className="seg">
        {PRESETS.map(([label, days]) => (
          <button
            key={label}
            type="button"
            className={q.to === t && q.from === addDays(t, -days) ? 'on' : ''}
            onClick={() => onChange({ ...q, from: addDays(t, -days), to: t })}
          >
            {label}
          </button>
        ))}
      </div>
      <label>
        기간 <input type="date" value={q.from} max={q.to} onChange={(e) => e.target.value && onChange({ ...q, from: e.target.value })} /> ~{' '}
        <input type="date" value={q.to} min={q.from} max={t} onChange={(e) => e.target.value && onChange({ ...q, to: e.target.value })} />
      </label>
      <label>
        기기{' '}
        <select value={q.device} onChange={(e) => onChange({ ...q, device: e.target.value as Device })}>
          <option value="ALL">전체</option>
          <option value="PC">PC</option>
          <option value="MO">모바일</option>
        </select>
      </label>
      {csv && (
        <a className="button" href={csv}>
          엑셀 다운로드
        </a>
      )}
    </div>
  )
}

// ---------- 수집 상태 ----------

/** 수집 꺼짐 경고 · 마지막 집계 시각 · (DS-01 수정 권한) 켜기/끄기 */
export function CollectBar({ settings, onChange }: { settings: DashSettings; onChange: (s: DashSettings) => void }) {
  const { can } = useMe()
  const { run, busy } = useRun()
  const toggle = () => {
    const next = !settings.enabled
    if (!next && !confirm('이용 통계 수집을 끕니다. 끈 동안의 방문은 기록되지 않습니다.')) return
    if (next && !confirm('이용 통계 수집을 켭니다.\n개인정보 처리 검토(처리방침 안내)를 마친 뒤에 켜 주세요.')) return
    void run(() => put<DashSettings>('/dashboard/settings', { enabled: next }), (s) => (s.enabled ? '수집을 켰습니다' : '수집을 껐습니다')).then(
      (s) => s && onChange(s),
    )
  }
  return (
    <div className={'collect-bar' + (settings.enabled ? '' : ' off')}>
      {settings.enabled ? <Badge tone="green">수집 중</Badge> : <Badge tone="amber">수집 꺼짐</Badge>}
      <span>
        {settings.enabled
          ? '고객 화면 이용 통계를 모으고 있습니다 (익명 · 개인정보 없음).'
          : '이용 통계 수집이 꺼져 있습니다. 켜기 전까지 새 방문은 기록되지 않습니다.'}
      </span>
      <span className="muted">마지막 집계 {settings.aggregatedAt ? fullDateTime(settings.aggregatedAt) : '없음'} · 1시간마다 갱신</span>
      {can('DS-01', 'EDIT') && (
        <button type="button" disabled={busy} onClick={toggle}>
          {settings.enabled ? '수집 끄기' : '수집 켜기'}
        </button>
      )}
    </div>
  )
}

// ---------- 숫자 · 표시 ----------

export const n = (v: number) => v.toLocaleString()

/** 비율 (%) — 분모 0 이면 – */
export const pct = (part: number, whole: number) => (whole > 0 ? `${((part * 100) / whole).toFixed(1)}%` : '–')

export const seconds = (ms: number, count: number) => (count > 0 ? `${Math.round(ms / 1000 / count)}초` : '–')

/** 직전 같은 기간 대비 */
function Delta({ cur, prev }: { cur: number; prev: number }) {
  if (prev === 0) return cur > 0 ? <span className="delta up">신규</span> : <span className="delta">–</span>
  const d = ((cur - prev) * 100) / prev
  const cls = d > 0 ? 'up' : d < 0 ? 'down' : ''
  return (
    <span className={'delta ' + cls}>
      {d > 0 ? '▲' : d < 0 ? '▼' : ''} {Math.abs(d).toFixed(1)}%
    </span>
  )
}

export function Kpi({ label, value, prev, suffix, hint }: { label: string; value: number; prev?: number; suffix?: string; hint?: string }) {
  return (
    <div className="kpi" title={hint}>
      <span>{label}</span>
      <b className="num">
        {suffix === '%' ? value.toFixed(1) : n(value)}
        {suffix && <small>{suffix}</small>}
      </b>
      {prev !== undefined && <Delta cur={value} prev={prev} />}
    </div>
  )
}

export function Card({ title, note, children, wide }: { title: string; note?: string; children: ReactNode; wide?: boolean }) {
  return (
    <section className={'dash-card' + (wide ? ' wide' : '')}>
      <header>
        <h2>{title}</h2>
        {note && <span className="muted">{note}</span>}
      </header>
      {children}
    </section>
  )
}

/** 가로 막대 목록 (값 · 비중) */
export function BarList({ rows, empty = '아직 데이터가 없습니다' }: { rows: { label: string; value: number; sub?: string }[]; empty?: string }) {
  if (rows.length === 0) return <div className="dash-empty">{empty}</div>
  const max = Math.max(...rows.map((r) => r.value), 1)
  const total = rows.reduce((s, r) => s + r.value, 0)
  return (
    <ul className="bar-list">
      {rows.map((r) => (
        <li key={r.label}>
          <span className="bl-label">{r.label}</span>
          <span className="bl-track">
            <span className="bl-fill" style={{ width: `${(r.value * 100) / max}%` }} />
          </span>
          <span className="bl-value num">
            {n(r.value)} <small>{r.sub ?? pct(r.value, total)}</small>
          </span>
        </li>
      ))}
    </ul>
  )
}

/** 일별 추이 (선 그래프, 계열 2개까지) */
export function LineChart({ days, series }: { days: string[]; series: { label: string; values: number[]; tone: 'coral' | 'navy' }[] }) {
  const W = 720
  const H = 200
  const P = { l: 40, r: 12, t: 12, b: 24 }
  const max = Math.max(1, ...series.flatMap((s) => s.values))
  const nice = Math.pow(10, Math.floor(Math.log10(max)))
  const top = Math.ceil(max / nice) * nice
  const x = (i: number) => P.l + (days.length <= 1 ? (W - P.l - P.r) / 2 : (i * (W - P.l - P.r)) / (days.length - 1))
  const y = (v: number) => P.t + (H - P.t - P.b) * (1 - v / top)
  const step = Math.max(1, Math.ceil(days.length / 8))
  if (days.length === 0) return <div className="dash-empty">아직 데이터가 없습니다</div>
  return (
    <div className="line-chart">
      <div className="legend">
        {series.map((s) => (
          <span key={s.label} className={'lg ' + s.tone}>
            {s.label}
          </span>
        ))}
      </div>
      <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={series.map((s) => s.label).join(' · ') + ' 일별 추이'}>
        {[0, 0.5, 1].map((f) => (
          <g key={f}>
            <line x1={P.l} x2={W - P.r} y1={y(top * f)} y2={y(top * f)} className="grid-line" />
            <text x={P.l - 6} y={y(top * f) + 4} textAnchor="end" className="axis">
              {n(Math.round(top * f))}
            </text>
          </g>
        ))}
        {days.map((d, i) =>
          i % step === 0 || i === days.length - 1 ? (
            <text key={d} x={x(i)} y={H - 6} textAnchor="middle" className="axis">
              {d.slice(5).replace('-', '/')}
            </text>
          ) : null,
        )}
        {series.map((s) => (
          <g key={s.label} className={'series ' + s.tone}>
            <polyline fill="none" points={s.values.map((v, i) => `${x(i)},${y(v)}`).join(' ')} />
            {s.values.map((v, i) => (
              <circle key={i} cx={x(i)} cy={y(v)} r={days.length > 45 ? 0 : 3}>
                <title>
                  {days[i]} {s.label} {n(v)}
                </title>
              </circle>
            ))}
          </g>
        ))}
      </svg>
    </div>
  )
}

// ---------- 이름표 ----------

export const REF: Record<string, string> = { SEARCH: '검색', SOCIAL: '소셜 · 메신저', DIRECT: '직접 방문', OTHER: '다른 사이트' }
export const DEVICE: Record<string, string> = { PC: 'PC', MO: '모바일' }
export const PAGE: Record<string, string> = {
  HOME: '메인',
  PLAN: '요금제 상세',
  AI_LIST: '알뜰AI 목록',
  AI_POST: '알뜰AI 글',
  FAQ: '자주 묻는 질문',
  TERMS: '약관',
  OUT: '이동 안내 (개통하기 · 상담 신청)',
  NOT_FOUND: '없는 페이지',
}
export const POSITION: Record<string, string> = {
  HERO: '히어로 카드 (11월 이전)',
  MONTHLY: '금주의 요금을 추천해요',
  PARTNER: '제휴사별 요금제',
  PREPAID: '선불요금제',
  CALC: '요금 계산기',
  INTERNET: '인터넷',
  ANALYSIS: '요금제 분석',
  SIMILAR: '비슷한 요금제 (상세)',
  DETAIL: '요금제 상세 (개통하기 등)',
  AI_POST: '알뜰AI 글',
  AI_LIST: '알뜰AI 목록',
  HEADER: '머리말 메뉴',
  FOOTER: '꼬리말',
  OTHER: '기타',
}
const DATA_ORDER = ['LT5', '5_10', '10_30', '30_100', 'GE100', 'U']
const DATA: Record<string, string> = { LT5: '5GB 미만', '5_10': '5~10GB', '10_30': '10~30GB', '30_100': '30~100GB', GE100: '100GB 이상', U: '무제한' }
const PRICE_ORDER = ['LT1', '1_2', '2_3', '3_4', 'GE4']
const PRICE: Record<string, string> = { LT1: '1만원 미만', '1_2': '1~2만원', '2_3': '2~3만원', '3_4': '3~4만원', GE4: '4만원 이상' }
const CALC_ORDER = ['1', '2', '3', '5', '7', '11', '30', 'U']
const PLAN_TYPE: Record<string, string> = { POSTPAID: '후불', PREPAID: '선불' }
const PRODUCT_TYPE: Record<string, string> = { SINGLE: '인터넷 단독', BUNDLE: '인터넷 + TV 결합', all: '전체' }
const gbLabel = (v: string) => (v === 'U' ? '무제한' : `${v}GB`)

/** 관심사 항목 → 막대 목록 (값 이름 · 정해진 순서) */
export function dimRows(data: Pick<DashInterest, 'dims'>, dim: string, partnerName: (code: string) => string) {
  const list = data.dims[dim] ?? []
  const order = dim === 'VIEW_DATA' ? DATA_ORDER : dim === 'VIEW_PRICE' ? PRICE_ORDER : dim === 'CALC_DATA' ? CALC_ORDER : null
  const sorted = order ? [...list].sort((a, b) => order.indexOf(a.value) - order.indexOf(b.value)) : list
  return sorted.map((i) => ({ label: valueLabel(dim, i.value, partnerName), value: i.cnt }))
}

export function valueLabel(dim: string, v: string, partnerName: (code: string) => string): string {
  switch (dim) {
    case 'CALC_DATA':
      return gbLabel(v)
    case 'CALC_ZERO': {
      const [gb, net] = v.split('|')
      return `${gbLabel(gb)} · ${NETWORK[net] ?? net}`
    }
    case 'CALC_NETWORK':
    case 'VIEW_NETWORK':
      return NETWORK[v] ?? v
    case 'VIEW_TYPE':
      return PLAN_TYPE[v] ?? v
    case 'VIEW_DATA':
      return DATA[v] ?? v
    case 'VIEW_PRICE':
      return PRICE[v] ?? v
    case 'VIEW_PARTNER':
    case 'PARTNER_TAB':
      return v === 'all' ? '전체' : partnerName(v)
    case 'INTERNET_TAB':
    case 'INTERNET_TYPE':
      return PRODUCT_TYPE[v] ?? v
    case 'INTERNET_CARRIER':
      return INTERNET_CARRIER[v] ?? v
    case 'CLICK_POS':
      return POSITION[v] ?? v
    default:
      return v
  }
}

export { PLAN_TYPE }

import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Badge, Empty, Loading, Modal, PageHead, Pager, Tabs, selectAll } from '../components/ui'
import { get, post, qs } from '../lib/api'
import { NETWORK, PLAN_STATE, dateTime, localToIso, nextHourLocal, won } from '../lib/format'
import { useMe } from '../lib/me'
import { bulkText, useRun } from '../lib/notice'
import type { BulkResult, PlanRow, PlanState } from '../lib/types'
import { useLoad } from '../lib/useLoad'
import { usePartnerOptions } from '../lib/usePartnerOptions'
import PlanCalendar from './PlanCalendar'

interface ListItem {
  plan: PlanRow
  state: PlanState
  missing: string[]
}

interface Page {
  total: number
  page: number
  items: ListItem[]
}

export const STATE_TONE: Record<string, 'green' | 'coral' | 'amber' | 'red' | 'gray'> = {
  PUBLISHED: 'green',
  DRAFT: 'coral',
  SCHEDULED: 'amber',
  ENDED: 'red',
  HIDDEN: 'gray',
}

const TYPE_TABS: [string, string][] = [['POSTPAID', '후불'], ['PREPAID', '선불'], ['CALENDAR', '게시 일정']]

/** PR-01 요금제관리 [후불] [선불] [게시 일정] */
export default function Plans() {
  const [params, setParams] = useSearchParams()
  if (params.get('type') !== 'CALENDAR') return <PlanList />
  return (
    <>
      <PageHead title="요금제관리" />
      <div className="filters">
        <Tabs value="CALENDAR" onChange={(v) => setParams(v === 'POSTPAID' ? {} : { type: v })} tabs={TYPE_TABS} />
      </div>
      <PlanCalendar />
    </>
  )
}

/** [후불] · [선불]: 보완 입력 · 게시 예약 · 즉시 게시 */
function PlanList() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const { can, reload: reloadMe } = useMe()
  const { run, busy } = useRun()
  const partners = usePartnerOptions()
  const type = params.get('type') === 'PREPAID' ? 'PREPAID' : 'POSTPAID'
  const state = params.get('state') ?? ''
  const partner = params.get('partner') ?? ''
  const network = params.get('network') ?? ''
  const [q, setQ] = useState(params.get('q') ?? '')
  const page = Number(params.get('page') ?? 1)
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [scheduling, setScheduling] = useState(false)
  const [publishAt, setPublishAt] = useState(nextHourLocal())

  const set = (key: string, value: string) => {
    const n = new URLSearchParams(params)
    if (value) n.set(key, value)
    else n.delete(key)
    if (key !== 'page') n.delete('page')
    setParams(n)
    setSelected(new Set())
  }

  const { data, error, reload } = useLoad(
    () => get<Page>('/plans' + qs({ type, state, partner, network, q: params.get('q'), page })),
    [type, state, partner, network, params.get('q'), page],
  )
  const items = data?.items ?? []
  const after = () => {
    setSelected(new Set())
    void reload()
    void reloadMe()
  }
  const canApprove = can('PR-01', 'APPROVE')

  return (
    <>
      <PageHead title="요금제관리">
        {canApprove && (
          <>
            <button disabled={busy || selected.size === 0} onClick={() => run(() => post<BulkResult>('/plans/cancel-schedule', { ids: [...selected] }), (r) => bulkText('예약 취소', r)).then(after)}>
              예약 취소
            </button>
            <button disabled={busy || selected.size === 0} onClick={() => setScheduling(true)}>
              선택 게시 예약 📅
            </button>
            <button
              className="primary"
              disabled={busy || selected.size === 0}
              onClick={() => {
                if (window.confirm(`선택한 ${selected.size}건을 지금 게시합니다. 프런트에 바로 반영됩니다.`)) {
                  void run(() => post<BulkResult>('/plans/publish', { ids: [...selected] }), (r) => bulkText('게시', r)).then(after)
                }
              }}
            >
              선택 즉시 게시
            </button>
          </>
        )}
      </PageHead>

      <div className="filters">
        <Tabs value={type} onChange={(v) => (v === 'CALENDAR' ? setParams({ type: v }) : set('type', v === 'POSTPAID' ? '' : v))} tabs={TYPE_TABS} />
        <div className="filter-row">
          <label>
            상태{' '}
            <select value={state} onChange={(e) => set('state', e.target.value)}>
              <option value="">전체</option>
              {Object.entries(PLAN_STATE).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </label>
          <label>
            제휴사{' '}
            <select value={partner} onChange={(e) => set('partner', e.target.value)}>
              <option value="">전체</option>
              {partners.map(([code, name]) => (
                <option key={code} value={code}>
                  {name}
                </option>
              ))}
            </select>
          </label>
          <label>
            망{' '}
            <select value={network} onChange={(e) => set('network', e.target.value)}>
              <option value="">전체</option>
              {Object.entries(NETWORK).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </label>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              set('q', q.trim())
            }}
          >
            <input placeholder="요금제명 검색" value={q} onChange={(e) => setQ(e.target.value)} maxLength={50} />
            <button>검색</button>
          </form>
        </div>
      </div>

      {!data ? (
        <Loading error={error} />
      ) : items.length === 0 ? (
        <Empty>해당하는 요금제가 없습니다.</Empty>
      ) : (
        <table className="grid">
          <thead>
            <tr>
              <th className="check">
                <input
                  type="checkbox"
                  aria-label="모두 선택"
                  checked={selectAll(items.map((i) => i.plan), selected)}
                  onChange={(e) => setSelected(e.target.checked ? new Set(items.map((i) => i.plan.id)) : new Set())}
                />
              </th>
              <th>제휴사</th>
              <th>요금제</th>
              <th className="num">{type === 'PREPAID' ? '충전 금액' : '월 요금'}</th>
              <th>망</th>
              <th>상태</th>
              <th>게시 일시</th>
              <th>최종 수집</th>
            </tr>
          </thead>
          <tbody>
            {items.map(({ plan: p, state: st, missing }) => (
              <tr key={p.id} className="clickable" onClick={() => navigate(`/plans/${p.id}`)}>
                <td className="check" onClick={(e) => e.stopPropagation()}>
                  <input
                    type="checkbox"
                    aria-label={`${p.name} 선택`}
                    checked={selected.has(p.id)}
                    onChange={() =>
                      setSelected((cur) => {
                        const n = new Set(cur)
                        if (n.has(p.id)) n.delete(p.id)
                        else n.add(p.id)
                        return n
                      })
                    }
                  />
                </td>
                <td>{p.partnerName}</td>
                <td>
                  {p.name}
                  {p.publishedVersionId === null && st !== 'ENDED' && <Badge tone="coral">신규</Badge>}
                </td>
                <td className="num">{won(p.price)}</td>
                <td>{NETWORK[p.network] ?? p.network}</td>
                <td>
                  <Badge tone={STATE_TONE[st]}>{PLAN_STATE[st]}</Badge>
                </td>
                <td>
                  {st === 'SCHEDULED' ? `📅 ${dateTime(p.publishAt)}` : st === 'ENDED' ? `${p.endedOn ?? ''} 종료` : dateTime(p.publishedAt)}
                  {missing.length > 0 && (
                    <Badge tone="amber">
                      ⚠ {missing.join(' · ')} 없음
                    </Badge>
                  )}
                </td>
                <td>{p.lastCollectedOn ?? '–'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {data && <Pager page={data.page} total={data.total} onChange={(p) => set('page', String(p))} />}

      {scheduling && (
        <Modal title={`${selected.size}건 게시 예약`} onClose={() => setScheduling(false)}>
          <div className="form-line">
            <label>
              게시 일시 <input type="datetime-local" step={600} value={publishAt} onChange={(e) => setPublishAt(e.target.value)} />
            </label>
          </div>
          <p className="hint">10분 단위로 지정합니다. 필수 항목이 빈 요금제는 예약되지 않습니다. 예약 시각까지는 지금 게시 중인 버전이 계속 노출됩니다.</p>
          <div className="actions">
            <button onClick={() => setScheduling(false)}>취소</button>
            <button
              className="primary"
              disabled={busy}
              onClick={() =>
                run(() => post<BulkResult>('/plans/schedule', { ids: [...selected], publishAt: localToIso(publishAt) }), (r) => bulkText('게시 예약', r)).then((r) => {
                  if (r) {
                    setScheduling(false)
                    after()
                  }
                })
              }
            >
              예약
            </button>
          </div>
        </Modal>
      )}
    </>
  )
}

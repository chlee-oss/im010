import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Badge, Empty, Loading, Modal, PageHead, Pager, Tabs, selectAll } from '../components/ui'
import { get, post, put, qs } from '../lib/api'
import { NETWORK, PLAN_STATE, dateTime, localToIso, nextHourLocal, planChangeLabel, won } from '../lib/format'
import { useMe } from '../lib/me'
import { bulkText, useRun } from '../lib/notice'
import type { BulkResult, PlanChange, PlanRow, PlanState } from '../lib/types'
import { useLoad } from '../lib/useLoad'
import { usePartnerOptions } from '../lib/usePartnerOptions'
import PlanCalendar from './PlanCalendar'

interface ListItem {
  plan: PlanRow
  state: PlanState
  change: PlanChange
  missing: string[]
}

interface Page {
  total: number
  page: number
  items: ListItem[]
}

export const STATE_TONE: Record<string, 'green' | 'coral' | 'amber' | 'red' | 'gray'> = {
  PUBLISHED: 'green',
  UNPUBLISHED: 'coral',
  HIDDEN: 'gray',
  ENDED: 'red',
}

/** 게시하지 않은 변경 배지 색: 대기 = 코랄, 예약 = 노랑 */
export const CHANGE_TONE: Record<string, 'coral' | 'amber'> = { DRAFT: 'coral', SCHEDULED: 'amber' }

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

type BulkTarget = 'HIDDEN' | 'VISIBLE' | 'ENDED'

const BULK_TARGETS: { value: BulkTarget; label: string; result: string; from: string; note: string }[] = [
  {
    value: 'HIDDEN',
    label: '비노출',
    result: '비노출 변경',
    from: '판매 종료 · 이미 비노출이 아닌 요금제',
    note: '고객 화면 목록 · 비교에서 빠집니다. 판매 종료와 달리 상세 주소는 그대로 두고, 언제든 노출로 되돌릴 수 있습니다.',
  },
  {
    value: 'VISIBLE',
    label: '노출 (비노출 해제)',
    result: '노출 변경',
    from: '비노출 요금제',
    note: '비노출 전 상태(게시 중 · 게시 전)로 돌아갑니다.',
  },
  {
    value: 'ENDED',
    label: '판매 종료',
    result: '판매 종료',
    from: '판매 종료가 아닌 요금제',
    note: '오늘 날짜로 판매 종료합니다. 상세 주소는 다음 미리 렌더링(매일 05:00)부터 410(종료 안내)으로 바뀌고, 이달의 요금제 노출은 내려가며, 남은 게시 예약은 취소됩니다. 제휴사 사이트에 계속 올라와 있으면 다음 수집 때 "판매 재개" 점검 항목으로 다시 올라옵니다.',
  },
]

/** 선택한 요금제 상태 일괄 변경 (PR-01 승인 권한) */
function StatusModal({
  items,
  busy,
  onClose,
  onSubmit,
}: {
  items: ListItem[]
  busy: boolean
  onClose: () => void
  onSubmit: (status: BulkTarget, label: string) => void
}) {
  const [target, setTarget] = useState<BulkTarget>('HIDDEN')
  const t = BULK_TARGETS.find((x) => x.value === target)!
  const counts = items.reduce<Record<string, number>>((m, i) => ({ ...m, [i.state]: (m[i.state] ?? 0) + 1 }), {})
  const applies = (st: PlanState) => (target === 'HIDDEN' ? st !== 'ENDED' && st !== 'HIDDEN' : target === 'VISIBLE' ? st === 'HIDDEN' : st !== 'ENDED')
  const n = items.filter((i) => applies(i.state)).length
  return (
    <Modal title={`${items.length}건 상태 변경`} onClose={onClose}>
      <p className="hint">
        선택: {Object.entries(counts).map(([st, c]) => `${PLAN_STATE[st] ?? st} ${c}건`).join(' · ')}
      </p>
      <div className="status-options">
        {BULK_TARGETS.map((x) => (
          <label key={x.value} className="inline">
            <input type="radio" name="bulk-status" checked={target === x.value} onChange={() => setTarget(x.value)} /> {x.label}
          </label>
        ))}
      </div>
      <p className="note">{t.note}</p>
      <p className="hint">
        바뀌는 대상: {t.from} — <b>{n}건</b>
        {n < items.length && ` (나머지 ${items.length - n}건은 건너뜁니다)`}
      </p>
      <div className="actions">
        <button onClick={onClose}>취소</button>
        <button
          className="primary"
          disabled={busy || n === 0}
          onClick={() => {
            if (target !== 'ENDED' || window.confirm(`${n}건을 판매 종료합니다. 고객 화면에서 판매 종료로 표시됩니다.`)) onSubmit(target, t.result)
          }}
        >
          {t.label.replace(' (비노출 해제)', '')}로 변경
        </button>
      </div>
    </Modal>
  )
}

/** [후불] · [선불]: 보완 입력 · 게시 예약 · 즉시 게시 · 상태 일괄 변경 */
function PlanList() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const { can, reload: reloadMe } = useMe()
  const { run, busy } = useRun()
  const partners = usePartnerOptions()
  const type = params.get('type') === 'PREPAID' ? 'PREPAID' : 'POSTPAID'
  const state = params.get('state') ?? ''
  const change = params.get('change') ?? ''
  const partner = params.get('partner') ?? ''
  const network = params.get('network') ?? ''
  const [q, setQ] = useState(params.get('q') ?? '')
  const page = Number(params.get('page') ?? 1)
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [scheduling, setScheduling] = useState(false)
  const [changing, setChanging] = useState(false)
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
    () => get<Page>('/plans' + qs({ type, state, change, partner, network, q: params.get('q'), page })),
    [type, state, change, partner, network, params.get('q'), page],
  )
  const items = data?.items ?? []
  const after = () => {
    setSelected(new Set())
    void reload()
    void reloadMe()
  }
  const canApprove = can('PR-01', 'APPROVE')
  const canEdit = can('PR-01', 'EDIT')
  const setMainPick = (id: number, value: string) =>
    run(
      () => put(`/plans/${id}/main-pick`, { mainPick: value ? Number(value) : null }),
      () => (value ? `메인 ${value}번째로 지정했습니다 (같은 제휴사의 기존 ${value}번째는 해제)` : '메인 노출을 해제했습니다'),
    ).then(() => reload())

  return (
    <>
      <PageHead title="요금제관리">
        {canApprove && (
          <>
            <button disabled={busy || selected.size === 0} onClick={() => setChanging(true)}>
              선택 상태 변경
            </button>
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
              <option value="">전체 (판매 종료 제외)</option>
              {Object.entries(PLAN_STATE).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </label>
          <label title="게시하지 않은 새 값이 있는 요금제. 게시할 때까지 고객 화면은 지금 값 그대로입니다">
            변경{' '}
            <select value={change} onChange={(e) => set('change', e.target.value)}>
              <option value="">전체</option>
              <option value="DRAFT">대기</option>
              <option value="SCHEDULED">예약</option>
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
              <th title="고객 화면 기준. 게시 중만 고객에게 보입니다">상태</th>
              <th title="게시하지 않은 새 값이 있음. 목록의 이름 · 요금 · 할인은 고객에게 보이는 값이고, 새 값은 상세 화면에서 비교합니다">변경</th>
              {type === 'POSTPAID' && (
                <>
                  <th className="num">할인 기간</th>
                  <th className="num">할인 후 요금</th>
                </>
              )}
              <th>게시 일시</th>
              <th>최종 수집</th>
              {type === 'POSTPAID' && (
                <th title="메인 '제휴사별 요금제'에 제휴사마다 2개씩 보입니다. 지정하지 않은 자리는 게시 중 요금제 중 월 요금이 낮은 순">메인 노출</th>
              )}
            </tr>
          </thead>
          <tbody>
            {items.map(({ plan: p, state: st, change: ch, missing }) => (
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
                </td>
                <td className="num">{won(p.price)}</td>
                <td>{NETWORK[p.network] ?? p.network}</td>
                <td>
                  <Badge tone={STATE_TONE[st]}>{PLAN_STATE[st]}</Badge>
                </td>
                <td>
                  {ch ? (
                    <Badge tone={CHANGE_TONE[ch]}>
                      {planChangeLabel(st, ch)}
                      {ch === 'SCHEDULED' && ` 📅 ${dateTime(p.publishAt)}`}
                    </Badge>
                  ) : (
                    '–'
                  )}
                  {missing.length > 0 && (
                    <Badge tone="amber">
                      ⚠ {missing.join(' · ')} 없음
                    </Badge>
                  )}
                </td>
                {type === 'POSTPAID' && (
                  <>
                    <td className="num">{p.discountMonths ? `${p.discountMonths}개월` : '–'}</td>
                    <td className="num">{p.discountMonths && p.priceAfterDiscount != null ? won(p.priceAfterDiscount) : '–'}</td>
                  </>
                )}
                <td>
                  {st === 'ENDED' ? `${p.endedOn ?? ''} 종료` : p.publishedAt ? dateTime(p.publishedAt) : '–'}
                </td>
                <td>{p.lastCollectedOn ?? '–'}</td>
                {type === 'POSTPAID' && (
                  <td onClick={(e) => e.stopPropagation()}>
                    {canEdit && st !== 'ENDED' ? (
                      <select
                        aria-label={`${p.name} 메인 노출`}
                        value={p.mainPick ?? ''}
                        disabled={busy}
                        onChange={(e) => void setMainPick(p.id, e.target.value)}
                      >
                        <option value="">자동</option>
                        <option value="1">1번째</option>
                        <option value="2">2번째</option>
                      </select>
                    ) : p.mainPick ? (
                      <Badge tone="navy">{p.mainPick}번째</Badge>
                    ) : (
                      '–'
                    )}
                    {p.mainPick && st !== 'PUBLISHED' && <span className="small muted"> 게시 전엔 안 보임</span>}
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {data && <Pager page={data.page} total={data.total} onChange={(p) => set('page', String(p))} />}

      {changing && (
        <StatusModal
          items={items.filter((i) => selected.has(i.plan.id))}
          busy={busy}
          onClose={() => setChanging(false)}
          onSubmit={(status, label) =>
            run(() => post<BulkResult>('/plans/status', { ids: [...selected], status }), (r) => bulkText(label, r)).then((r) => {
              if (r) {
                setChanging(false)
                after()
              }
            })
          }
        />
      )}

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

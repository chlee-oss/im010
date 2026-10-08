import { useState } from 'react'
import ItemDrawer from '../components/ItemDrawer'
import { Badge, Empty, Loading, PageHead, Pager, Tabs, selectAll } from '../components/ui'
import { get, post, qs } from '../lib/api'
import { CHANGE, FIELD, ITEM_STATUS, URL_TYPE, WARNING, itemValueText } from '../lib/format'
import { useMe } from '../lib/me'
import { bulkText, useRun } from '../lib/notice'
import type { BulkResult, ItemPage } from '../lib/types'
import { useLoad } from '../lib/useLoad'
import { usePartnerOptions } from '../lib/usePartnerOptions'

type View = 'PENDING' | 'REQUESTED' | 'UNCHANGED' | 'ENDED' | 'EXCLUDED' | 'ALL'

/** BA-02 요금제배치관리: 수집 리스트 · 내용 점검 → 승인 요청 */
export default function BatchItems() {
  const { can, reload: reloadMe } = useMe()
  const { run, busy } = useRun()
  const [view, setView] = useState<View>('PENDING')
  const [collectedOn, setCollectedOn] = useState('')
  const [partner, setPartner] = useState('')
  const [urlType, setUrlType] = useState('')
  const [page, setPage] = useState(1)
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [open, setOpen] = useState<number | null>(null)

  const { data, error, reload } = useLoad(
    () => get<ItemPage>('/batch-items' + qs({ view, collectedOn, partner, urlType, page })),
    [view, collectedOn, partner, urlType, page],
  )
  const after = () => {
    setSelected(new Set())
    void reload()
    void reloadMe()
  }
  const s = data?.summary ?? {}
  const selectable = view === 'PENDING'
  const items = data?.items ?? []
  const options = usePartnerOptions()
  const partners = options.length ? options : [...new Map(items.map((i) => [i.partnerCode, i.partnerName])).entries()]

  const toggle = (id: number) =>
    setSelected((cur) => {
      const n = new Set(cur)
      if (n.has(id)) n.delete(id)
      else n.add(id)
      return n
    })

  return (
    <>
      <PageHead title="요금제배치관리">
        {selectable && can('BA-02', 'REVIEW') && (
          <>
            <button
              disabled={busy || selected.size === 0}
              onClick={() => {
                const reason = window.prompt(`선택한 ${selected.size}건을 제외합니다. 사유를 입력해 주세요.`)
                if (reason?.trim()) void run(() => post<BulkResult>('/batch-items/exclude', { ids: [...selected], reason }), (r) => bulkText('제외', r)).then(after)
              }}
            >
              선택 제외
            </button>
            <button
              className="primary"
              disabled={busy || selected.size === 0}
              onClick={() => run(() => post<BulkResult>('/batch-items/review', { ids: [...selected] }), (r) => bulkText('점검 완료 → 승인 요청', r)).then(after)}
            >
              선택 점검 완료 → 승인 요청
            </button>
          </>
        )}
      </PageHead>

      <div className="filters">
        <Tabs
          value={view}
          onChange={(v) => {
            setView(v)
            setPage(1)
            setSelected(new Set())
          }}
          tabs={[
            ['PENDING', <>점검 대기 <b>{s.pending ?? 0}</b> <small>(신규 {s.pending_new ?? 0} · 변경 {s.pending_changed ?? 0})</small></>],
            ['REQUESTED', <>승인 요청 <b>{s.requested ?? 0}</b></>],
            ['UNCHANGED', <>변경 없음 <b>{s.unchanged ?? 0}</b></>],
            ['ENDED', <>판매 종료 <b>{s.ended ?? 0}</b></>],
            ['EXCLUDED', <>제외 <b>{s.excluded ?? 0}</b></>],
            ['ALL', '수집 전체'],
          ]}
        />
        <div className="filter-row">
          {view !== 'PENDING' && view !== 'REQUESTED' && (
            <label>
              수집일 <input type="date" value={collectedOn || data?.collectedOn || ''} onChange={(e) => setCollectedOn(e.target.value)} />
            </label>
          )}
          <label>
            제휴사{' '}
            <select value={partner} onChange={(e) => { setPartner(e.target.value); setPage(1) }}>
              <option value="">전체</option>
              {partners.map(([code, name]) => (
                <option key={code} value={code}>
                  {name}
                </option>
              ))}
              {partner && !partners.some(([c]) => c === partner) && <option value={partner}>{partner}</option>}
            </select>
          </label>
          <label>
            구분{' '}
            <select value={urlType} onChange={(e) => { setUrlType(e.target.value); setPage(1) }}>
              <option value="">후불 · 선불 · 이달의</option>
              <option value="POSTPAID">후불</option>
              <option value="PREPAID">선불</option>
              <option value="MONTHLY">이달의 요금제</option>
            </select>
          </label>
          {view === 'PENDING' && <span className="muted">⚠ 경고가 있는 건은 한 건씩 열어 점검해 주세요 (일괄 점검에서 빠집니다)</span>}
        </div>
      </div>

      {!data ? (
        <Loading error={error} />
      ) : items.length === 0 ? (
        <Empty>{view === 'PENDING' ? '점검할 수집 건이 없습니다.' : '해당하는 수집 건이 없습니다.'}</Empty>
      ) : (
        <table className="grid">
          <thead>
            <tr>
              {selectable && (
                <th className="check">
                  <input
                    type="checkbox"
                    aria-label="모두 선택"
                    checked={selectAll(items, selected)}
                    onChange={(e) => setSelected(e.target.checked ? new Set(items.map((i) => i.id)) : new Set())}
                  />
                </th>
              )}
              <th>제휴사</th>
              <th>구분</th>
              <th>요금제</th>
              <th className="num">요금</th>
              <th>데이터</th>
              <th>변경 내용</th>
              <th>상태</th>
            </tr>
          </thead>
          <tbody>
            {items.map((i) => (
              <tr key={i.id} className="clickable" onClick={() => setOpen(i.id)}>
                {selectable && (
                  <td className="check" onClick={(e) => e.stopPropagation()}>
                    <input type="checkbox" aria-label={`${i.values.name} 선택`} checked={selected.has(i.id)} onChange={() => toggle(i.id)} />
                  </td>
                )}
                <td>{i.partnerName}</td>
                <td>{URL_TYPE[i.urlType]}</td>
                <td>
                  {i.values.name ?? '(이름 없음)'} {i.changeType === 'NEW' && <Badge tone="coral">신규</Badge>}
                  {i.changeType === 'ENDED' && <Badge tone="red">판매 종료</Badge>}
                  {i.editedFields.length > 0 && <Badge tone="navy">수정</Badge>}
                </td>
                <td className="num">{itemValueText('price', i.values as unknown as Record<string, unknown>)}</td>
                <td>{i.values.dataText ?? '–'}</td>
                <td>
                  {i.changeType === 'CHANGED' ? i.changedFields.map((f) => FIELD[f] ?? f).join(' · ') || '판매 재개' : CHANGE[i.changeType]}
                  {i.warnings.map((w) => (
                    <Badge key={w} tone="amber">
                      ⚠ {WARNING[w] ?? w}
                    </Badge>
                  ))}
                </td>
                <td>{ITEM_STATUS[i.status]}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {data && <Pager page={data.page} total={data.total} onChange={setPage} />}
      {open !== null && <ItemDrawer id={open} mode="review" onClose={() => setOpen(null)} onChanged={after} />}
    </>
  )
}

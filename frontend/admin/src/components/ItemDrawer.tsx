import { useState } from 'react'
import { get, post, put } from '../lib/api'
import { CHANGE, FIELD, ITEM_STATUS, URL_TYPE, WARNING, dateTime, itemValueText } from '../lib/format'
import { useMe } from '../lib/me'
import { useRun } from '../lib/notice'
import type { Item, ItemValues, PlanRow, Version } from '../lib/types'
import { useLoad } from '../lib/useLoad'
import { Badge, Drawer, Loading, Modal } from './ui'

interface Detail {
  item: Item
  current: Version | null
  plan: PlanRow | null
}

const COMPARE: [string, string][] = [
  ['name', 'name'],
  ['dataText', 'dataText'],
  ['dataGb', 'dataGb'],
  ['qos', 'qosText'],
  ['voice', 'voiceText'],
  ['sms', 'smsText'],
  ['network', 'network'],
  ['generation', 'generation'],
  ['price', 'price'],
  ['discountMonths', 'discountMonths'],
  ['priceAfterDiscount', 'priceAfterDiscount'],
]

function versionValues(v: Version | null): Record<string, unknown> | null {
  if (!v) return null
  return { ...v, price: v.monthlyPrice ?? v.chargePrice }
}

/**
 * 수집 건 상세 (BA-02 점검 · BA-03 승인 공통): 현재 승인값과 수집값 비교, 값 수정, 점검 · 제외 · 승인 · 반려.
 */
export default function ItemDrawer({ id, mode, onClose, onChanged }: { id: number; mode: 'review' | 'approve'; onClose: () => void; onChanged: () => void }) {
  const { can, reload: reloadMe } = useMe()
  const { run, busy } = useRun()
  const { data, error, reload } = useLoad(() => get<Detail>(`/${mode === 'review' ? 'batch-items' : 'approvals'}/${id}`), [id, mode])
  const [editing, setEditing] = useState<ItemValues | null>(null)
  const [memo, setMemo] = useState('')
  const [reason, setReason] = useState('')
  const [linking, setLinking] = useState(false)

  if (!data) return <Drawer title="수집 건" onClose={onClose}><Loading error={error} /></Drawer>
  const { item, current } = data
  const done = async () => {
    await reload()
    onChanged()
    void reloadMe()
  }
  const pending = item.status === 'REVIEW_PENDING'
  const requested = item.status === 'APPROVAL_REQUESTED'
  const monthly = item.urlType === 'MONTHLY'

  return (
    <Drawer
      title={
        <>
          {item.partnerName} · {URL_TYPE[item.urlType]} · {item.values.name ?? '(이름 없음)'}
        </>
      }
      onClose={onClose}
    >
      <div className="meta-row">
        <Badge tone={item.changeType === 'NEW' ? 'coral' : item.changeType === 'ENDED' ? 'red' : 'navy'}>{CHANGE[item.changeType]}</Badge>
        <Badge>{ITEM_STATUS[item.status]}</Badge>
        {item.warnings.map((w) => (
          <Badge key={w} tone="amber">
            ⚠ {WARNING[w] ?? w}
          </Badge>
        ))}
        <span className="muted">
          수집일 {item.collectedOn} · 코드 {item.partnerPlanCode ?? '–'}
          {item.siteOrder ? ` · 사이트 ${item.siteOrder}번째` : ''}
        </span>
        {item.detailUrl && (
          <a href={item.detailUrl} target="_blank" rel="noopener noreferrer" className="link">
            제휴사 페이지 열기 ↗
          </a>
        )}
      </div>

      {monthly ? (
        <p className="note">
          이달의 요금제 항목입니다. 승인하면 {item.planId ? `요금제 #${item.planId}` : '같은 코드의 요금제'}와 연결해 메인 이달의 요금제에 노출합니다.
          {!item.planId && ' 연결할 요금제가 아직 없으면 그 요금제를 먼저 승인해 주세요.'}
        </p>
      ) : (
        <table className="grid compare">
          <thead>
            <tr>
              <th>항목</th>
              <th>현재 승인값{data.plan ? ` (요금제 #${data.plan.id})` : ''}</th>
              <th>수집값</th>
            </tr>
          </thead>
          <tbody>
            {COMPARE.map(([field, key]) => {
              const changed = item.changedFields.includes(field)
              const edited = item.editedFields.includes(field)
              return (
                <tr key={field} className={changed ? 'changed' : ''}>
                  <th>{FIELD[field]}</th>
                  <td>{current ? itemValueText(key, versionValues(current)) : item.changeType === 'NEW' ? '신규' : '–'}</td>
                  <td>
                    {editing ? (
                      <ValueInput field={key as keyof ItemValues} values={editing} onChange={setEditing} />
                    ) : (
                      <>
                        {changed && '■ '}
                        {itemValueText(key, item.values as unknown as Record<string, unknown>)}
                        {edited && <Badge tone="navy">수정</Badge>}
                      </>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      )}

      {(item.memo || item.reason || item.reviewer) && (
        <dl className="history">
          {item.memo && (
            <>
              <dt>메모</dt>
              <dd>{item.memo}</dd>
            </>
          )}
          {item.reviewer && (
            <>
              <dt>점검</dt>
              <dd>
                {item.reviewer} · {dateTime(item.reviewedAt)}
              </dd>
            </>
          )}
          {item.reason && (
            <>
              <dt>사유</dt>
              <dd>{item.reason}</dd>
            </>
          )}
        </dl>
      )}

      {mode === 'review' && pending && editing && (
        <div className="actions">
          <input placeholder="메모 (선택)" value={memo} onChange={(e) => setMemo(e.target.value)} maxLength={500} />
          <button onClick={() => setEditing(null)}>취소</button>
          <button
            className="primary"
            disabled={busy}
            onClick={() =>
              run(() => put(`/batch-items/${id}`, { values: editing, memo }), () => '수정했습니다').then((r) => {
                if (r) {
                  setEditing(null)
                  void done()
                }
              })
            }
          >
            수정 저장
          </button>
        </div>
      )}

      {mode === 'review' && pending && !editing && (
        <div className="actions">
          {!monthly && can('BA-02', 'EDIT') && <button onClick={() => setEditing({ ...item.values })}>값 수정</button>}
          {!monthly && item.changeType === 'NEW' && can('BA-02', 'EDIT') && <button onClick={() => setLinking(true)}>기존 요금제와 연결</button>}
          {can('BA-02', 'REVIEW') && (
            <>
              <input placeholder="제외 사유" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} />
              <button disabled={busy || !reason.trim()} onClick={() => run(() => post('/batch-items/exclude', { ids: [id], reason }), () => '제외했습니다').then((r) => r && done())}>
                제외
              </button>
              <button className="primary" disabled={busy} onClick={() => run(() => post('/batch-items/review', { ids: [id] }), () => '점검 완료 — 승인 요청했습니다').then((r) => r && done())}>
                점검 완료 → 승인 요청
              </button>
            </>
          )}
        </div>
      )}

      {mode === 'review' && item.changeType === 'ENDED' && item.status === 'AUTO_APPLIED' && !monthly && can('BA-02', 'REVIEW') && (
        <div className="actions">
          <span className="muted">자동 판매 종료가 잘못됐다면</span>
          <button className="primary" disabled={busy} onClick={() => run(() => post(`/batch-items/${id}/resume`), () => '판매 재개를 승인 요청했습니다').then((r) => r && done())}>
            판매 재개 요청
          </button>
        </div>
      )}

      {mode === 'approve' && requested && can('BA-03', 'APPROVE') && (
        <div className="actions">
          <input placeholder="반려 사유" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} />
          <button disabled={busy || !reason.trim()} onClick={() => run(() => post('/approvals/reject', { ids: [id], reason }), () => '반려했습니다').then((r) => r && done())}>
            반려
          </button>
          <button className="primary" disabled={busy} onClick={() => run(() => post('/approvals/approve', { ids: [id] }), () => '승인 — 요금제관리로 이관했습니다').then((r) => r && done())}>
            승인 → 요금제관리로 이관
          </button>
        </div>
      )}
      {linking && (
        <LinkModal
          itemId={id}
          onClose={() => setLinking(false)}
          onLinked={() => {
            setLinking(false)
            void done()
          }}
        />
      )}
    </Drawer>
  )
}

/** [기존 요금제와 연결]: 이름(코드)만 바뀐 요금제를 신규가 아닌 기존 요금제의 변경으로 */
function LinkModal({ itemId, onClose, onLinked }: { itemId: number; onClose: () => void; onLinked: () => void }) {
  const { run, busy } = useRun()
  const { data, error } = useLoad(() => get<PlanRow[]>(`/batch-items/${itemId}/link-candidates`), [itemId])
  const [q, setQ] = useState('')
  const list = (data ?? []).filter((p) => !q.trim() || p.name.includes(q.trim()))
  return (
    <Modal title="기존 요금제와 연결" onClose={onClose}>
      <p className="hint">제휴사가 요금제 이름이나 코드만 바꾼 경우 연결하면, 승인할 때 기존 요금제의 새 버전이 되고 판매 종료로 잡힌 요금제는 되살아납니다.</p>
      <input placeholder="요금제명 검색" value={q} onChange={(e) => setQ(e.target.value)} style={{ width: '100%', margin: '8px 0' }} />
      {!data ? (
        <Loading error={error} />
      ) : (
        <div className="pick-list">
          {list.length === 0 && <p className="muted">연결할 수 있는 요금제가 없습니다.</p>}
          {list.map((p) => (
            <button key={p.id} disabled={busy} onClick={() => run(() => post(`/batch-items/${itemId}/link`, { planId: p.id }), () => `요금제 #${p.id}에 연결했습니다`).then((r) => r && onLinked())}>
              <span>
                #{p.id} {p.name}
              </span>
              <span className="muted">
                {p.status === 'ENDED' ? `판매 종료 ${p.endedOn ?? ''}` : p.status} · 코드 {p.partnerPlanCode ?? '–'}
              </span>
            </button>
          ))}
        </div>
      )}
    </Modal>
  )
}

const NUMBER_FIELDS = new Set(['dataGb', 'price', 'discountMonths', 'priceAfterDiscount'])

export function ValueInput({ field, values, onChange }: { field: keyof ItemValues; values: ItemValues; onChange: (v: ItemValues) => void }) {
  const value = values[field]
  const set = (v: string) => {
    const parsed = v === '' ? null : NUMBER_FIELDS.has(field) ? Number(v) : v
    onChange({ ...values, [field]: parsed })
  }
  if (field === 'network') {
    return (
      <select value={(value as string) ?? ''} onChange={(e) => set(e.target.value)}>
        <option value="">–</option>
        <option value="SKT">SKT</option>
        <option value="KT">KT</option>
        <option value="LGU">LG U+</option>
      </select>
    )
  }
  if (field === 'generation') {
    return (
      <select value={(value as string) ?? ''} onChange={(e) => set(e.target.value)}>
        <option value="">–</option>
        <option value="LTE">LTE</option>
        <option value="5G">5G</option>
      </select>
    )
  }
  return (
    <input
      type={NUMBER_FIELDS.has(field) ? 'number' : 'text'}
      step={field === 'dataGb' ? '0.01' : '1'}
      min={NUMBER_FIELDS.has(field) ? 0 : undefined}
      value={value === null || value === undefined ? '' : String(value)}
      onChange={(e) => set(e.target.value)}
    />
  )
}

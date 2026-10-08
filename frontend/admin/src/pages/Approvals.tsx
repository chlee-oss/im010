import { useState } from 'react'
import ItemDrawer from '../components/ItemDrawer'
import { Badge, Empty, Loading, Modal, PageHead, Pager, selectAll } from '../components/ui'
import { get, post, qs } from '../lib/api'
import { CHANGE, FIELD, URL_TYPE, WARNING, dateTime, itemValueText, localToIso, nextHourLocal } from '../lib/format'
import { useMe } from '../lib/me'
import { bulkText, useRun } from '../lib/notice'
import type { BulkResult, ItemPage } from '../lib/types'
import { useLoad } from '../lib/useLoad'

interface ApproveResult {
  approved: { id: number; planId: number | null; versionId: number | null; replacedSchedule: boolean; note: string | null }[]
  skipped: { id: number; reason: string }[]
}

/** BA-03 승인관리: 승인 = 요금제관리로 이관 ("게시 대기"), 반려 = 점검 대기로 */
export default function Approvals() {
  const { can, reload: reloadMe } = useMe()
  const { run, busy } = useRun()
  const [page, setPage] = useState(1)
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [open, setOpen] = useState<number | null>(null)
  const [approving, setApproving] = useState(false)
  const [withSchedule, setWithSchedule] = useState(false)
  const [publishAt, setPublishAt] = useState(nextHourLocal())

  const { data, error, reload } = useLoad(() => get<ItemPage>('/approvals' + qs({ page })), [page])
  const items = data?.items ?? []
  const after = () => {
    setSelected(new Set())
    void reload()
    void reloadMe()
  }
  const canApprove = can('BA-03', 'APPROVE')
  const canSchedule = can('PR-01', 'APPROVE')

  const approve = () =>
    run(
      () => post<ApproveResult>('/approvals/approve', { ids: [...selected], publishAt: withSchedule ? localToIso(publishAt) : null }),
      (r) => {
        const notes = r.approved.filter((a) => a.note || a.replacedSchedule).map((a) => `#${a.id} ${a.replacedSchedule ? '기존 예약 대체 ' : ''}${a.note ?? ''}`)
        return bulkText('승인 → 요금제관리로 이관', { done: r.approved.map((a) => a.id), skipped: r.skipped }) + (notes.length ? ' · ' + notes.join(', ') : '')
      },
    ).then(() => {
      setApproving(false)
      after()
    })

  return (
    <>
      <PageHead title="승인관리">
        {canApprove && (
          <>
            <button
              disabled={busy || selected.size === 0}
              onClick={() => {
                const reason = window.prompt(`선택한 ${selected.size}건을 반려합니다. 사유를 입력해 주세요.`)
                if (reason?.trim()) void run(() => post<BulkResult>('/approvals/reject', { ids: [...selected], reason }), (r) => bulkText('반려', r)).then(after)
              }}
            >
              반려
            </button>
            <button className="primary" disabled={busy || selected.size === 0} onClick={() => setApproving(true)}>
              선택 승인 → 요금제관리로 이관
            </button>
          </>
        )}
      </PageHead>
      <p className="note">승인하면 요금제관리에 "게시 대기"로 넘어가며, 아직 외부에 노출되지 않습니다. 외부 게시는 요금제관리의 게시 예약 · 즉시 게시로 합니다.</p>

      {!data ? (
        <Loading error={error} />
      ) : items.length === 0 ? (
        <Empty>승인 대기 중인 건이 없습니다.</Empty>
      ) : (
        <table className="grid">
          <thead>
            <tr>
              <th className="check">
                <input type="checkbox" aria-label="모두 선택" checked={selectAll(items, selected)} onChange={(e) => setSelected(e.target.checked ? new Set(items.map((i) => i.id)) : new Set())} />
              </th>
              <th>제휴사</th>
              <th>구분</th>
              <th>요금제</th>
              <th className="num">요금</th>
              <th>변경 요약</th>
              <th>요청자 · 일시</th>
            </tr>
          </thead>
          <tbody>
            {items.map((i) => (
              <tr key={i.id} className="clickable" onClick={() => setOpen(i.id)}>
                <td className="check" onClick={(e) => e.stopPropagation()}>
                  <input
                    type="checkbox"
                    aria-label={`${i.values.name} 선택`}
                    checked={selected.has(i.id)}
                    onChange={() =>
                      setSelected((cur) => {
                        const n = new Set(cur)
                        if (n.has(i.id)) n.delete(i.id)
                        else n.add(i.id)
                        return n
                      })
                    }
                  />
                </td>
                <td>{i.partnerName}</td>
                <td>{URL_TYPE[i.urlType]}</td>
                <td>
                  {i.values.name} {i.changeType === 'NEW' && <Badge tone="coral">신규</Badge>}
                </td>
                <td className="num">{itemValueText('price', i.values as unknown as Record<string, unknown>)}</td>
                <td>
                  {i.changeType === 'ENDED'
                    ? '판매 재개'
                    : i.changeType === 'CHANGED'
                      ? i.changedFields.map((f) => FIELD[f] ?? f).join(' · ') || '판매 재개'
                      : CHANGE[i.changeType]}
                  {i.warnings.map((w) => (
                    <Badge key={w} tone="amber">
                      ⚠ {WARNING[w] ?? w}
                    </Badge>
                  ))}
                </td>
                <td>
                  {i.reviewer} · {dateTime(i.reviewedAt)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {data && <Pager page={data.page} total={data.total} onChange={setPage} />}

      {approving && (
        <Modal title={`${selected.size}건 승인`} onClose={() => setApproving(false)}>
          <p>요금제관리로 이관하고 "게시 대기"로 둡니다.</p>
          {canSchedule && (
            <div className="form-line">
              <label className="inline">
                <input type="checkbox" checked={withSchedule} onChange={(e) => setWithSchedule(e.target.checked)} /> 승인과 함께 게시 예약
              </label>
              {withSchedule && <input type="datetime-local" step={600} value={publishAt} onChange={(e) => setPublishAt(e.target.value)} />}
            </div>
          )}
          {withSchedule && <p className="hint">필수 항목(개통 URL · 선불 사용 기간 등)이 빈 요금제는 예약하지 않고 승인만 합니다.</p>}
          <div className="actions">
            <button onClick={() => setApproving(false)}>취소</button>
            <button className="primary" disabled={busy} onClick={approve}>
              승인
            </button>
          </div>
        </Modal>
      )}
      {open !== null && <ItemDrawer id={open} mode="approve" onClose={() => setOpen(null)} onChanged={after} />}
    </>
  )
}

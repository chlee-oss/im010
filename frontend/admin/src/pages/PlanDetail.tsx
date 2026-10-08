import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ValueInput } from '../components/ItemDrawer'
import { Badge, Loading, Modal, PageHead } from '../components/ui'
import { get, post, put } from '../lib/api'
import { FIELD, NETWORK, PLAN_STATE, URL_TYPE, dateTime, itemValueText, localToIso, nextHourLocal } from '../lib/format'
import { useMe } from '../lib/me'
import { bulkText, useRun } from '../lib/notice'
import type { BulkResult, ItemValues, PlanDetail as Detail, Version } from '../lib/types'
import { useLoad } from '../lib/useLoad'
import { STATE_TONE } from './Plans'

const FORM_FIELDS: [keyof ItemValues, string][] = [
  ['name', 'name'],
  ['dataText', 'dataText'],
  ['dataGb', 'dataGb'],
  ['qosText', 'qos'],
  ['voiceText', 'voice'],
  ['smsText', 'sms'],
  ['network', 'network'],
  ['generation', 'generation'],
  ['price', 'price'],
  ['discountMonths', 'discountMonths'],
  ['priceAfterDiscount', 'priceAfterDiscount'],
]

function toValues(v: Version): ItemValues {
  return {
    name: v.name,
    dataText: v.dataText,
    dataGb: v.dataGb,
    qosText: v.qosText,
    voiceText: v.voiceText,
    smsText: v.smsText,
    network: v.network,
    generation: v.generation,
    price: v.monthlyPrice ?? v.chargePrice,
    discountMonths: v.discountMonths,
    priceAfterDiscount: v.priceAfterDiscount,
  }
}

/** PR-01 요금제 상세: 게시 중 · 대기 버전 비교, 보완 입력, 개통 URL, 게시 예약 · 즉시 게시 · 비노출 · 롤백, 버전 이력 */
export default function PlanDetail() {
  const { id } = useParams()
  const { can, reload: reloadMe } = useMe()
  const { run, busy } = useRun()
  const { data, error, reload, setData } = useLoad(() => get<Detail>(`/plans/${id}`), [id])
  const [editing, setEditing] = useState<{ values: ItemValues; validDays: number | null; tags: string } | null>(null)
  const [url, setUrl] = useState<string | null>(null)
  const [scheduling, setScheduling] = useState(false)
  const [publishAt, setPublishAt] = useState(nextHourLocal())

  if (!data) return <Loading error={error} />
  const { plan, state, published, draft, versions, missing } = data
  const prepaid = plan.planType === 'PREPAID'
  const canEdit = can('PR-01', 'EDIT') && state !== 'ENDED'
  const canApprove = can('PR-01', 'APPROVE')
  const base = draft ?? published
  const done = (d?: Detail | BulkResult) => {
    if (d && 'plan' in d) setData(d)
    else void reload()
    void reloadMe()
  }

  return (
    <>
      <PageHead title={plan.name}>
        <Link to="/plans" className="button">
          ← 목록
        </Link>
      </PageHead>
      <div className="meta-row">
        <Badge tone={STATE_TONE[state]}>{PLAN_STATE[state]}</Badge>
        <span>
          {plan.partnerName} · {URL_TYPE[plan.planType]} · {NETWORK[plan.network] ?? plan.network} · 요금제 #{plan.id} · 제휴사 코드 {plan.partnerPlanCode ?? '–'}
        </span>
        <span className="muted">최종 수집 {plan.lastCollectedOn ?? '–'}</span>
      </div>

      <section className="panel">
        <h2>개통 URL</h2>
        {url === null ? (
          <div className="form-line">
            {plan.activationUrl ? (
              <a href={plan.activationUrl} target="_blank" rel="noopener noreferrer" className="link">
                {plan.activationUrl} ↗
              </a>
            ) : (
              <Badge tone="amber">⚠ 개통 URL 없음</Badge>
            )}
            {canEdit && <button onClick={() => setUrl(plan.activationUrl ?? 'https://')}>수정</button>}
          </div>
        ) : (
          <div className="form-line">
            <input className="wide" value={url} onChange={(e) => setUrl(e.target.value)} maxLength={500} />
            <button onClick={() => setUrl(null)}>취소</button>
            <button className="primary" disabled={busy} onClick={() => run(() => put<Detail>(`/plans/${id}/activation-url`, { url }), () => '개통 URL을 바꿨습니다').then((d) => { if (d) { setUrl(null); done(d) } })}>
              저장
            </button>
          </div>
        )}
        <p className="hint">프런트 `개통하기`는 im010 이동 주소(/go/{plan.id})를 거쳐 이 URL로 보냅니다 (RC-01 접수 기록).</p>
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>값 · 보완 입력</h2>
          {canEdit && base && !editing && (
            <button onClick={() => setEditing({ values: toValues(base), validDays: base.validDays, tags: base.tags.join(', ') })}>
              {draft ? '대기 버전 고치기' : '고치기 (새 대기 버전)'}
            </button>
          )}
        </div>
        {missing.length > 0 && <p className="form-error">⚠ 게시 전에 채워야 할 항목: {missing.join(', ')}</p>}
        <table className="grid compare">
          <thead>
            <tr>
              <th>항목</th>
              <th>게시 중{published ? ` (v${published.versionNo})` : ''}</th>
              <th>
                {draft ? (draft.state === 'SCHEDULED' ? `게시 예약 (v${draft.versionNo}) 📅 ${dateTime(draft.publishAt)}` : `게시 대기 (v${draft.versionNo})`) : '대기 버전 없음'}
              </th>
            </tr>
          </thead>
          <tbody>
            {FORM_FIELDS.map(([key, field]) => {
              const supplemented = draft?.supplementedFields.includes(field)
              const changed = draft?.changedFields.includes(field)
              return (
                <tr key={key} className={changed ? 'changed' : ''}>
                  <th>{FIELD[field]}</th>
                  <td>{published ? itemValueText(key, toValues(published) as unknown as Record<string, unknown>) : '–'}</td>
                  <td>
                    {editing ? (
                      <ValueInput field={key} values={editing.values} onChange={(v) => setEditing({ ...editing, values: v })} />
                    ) : draft ? (
                      <>
                        {itemValueText(key, toValues(draft) as unknown as Record<string, unknown>)}
                        {supplemented && <Badge tone="navy">보완</Badge>}
                      </>
                    ) : (
                      ''
                    )}
                  </td>
                </tr>
              )
            })}
            <tr>
              <th>{FIELD.validDays}</th>
              <td>{published?.validDays ? published.validDays + '일' : '–'}</td>
              <td>
                {editing ? (
                  <input type="number" min={1} value={editing.validDays ?? ''} onChange={(e) => setEditing({ ...editing, validDays: e.target.value === '' ? null : Number(e.target.value) })} />
                ) : draft?.validDays ? (
                  draft.validDays + '일'
                ) : (
                  ''
                )}
                {prepaid && <span className="hint"> 선불 필수</span>}
              </td>
            </tr>
            <tr>
              <th>{FIELD.tags}</th>
              <td>{published?.tags.join(', ') || '–'}</td>
              <td>
                {editing ? (
                  <input className="wide" placeholder="쉼표로 구분 (최대 6개)" value={editing.tags} onChange={(e) => setEditing({ ...editing, tags: e.target.value })} />
                ) : (
                  draft?.tags.join(', ')
                )}
              </td>
            </tr>
          </tbody>
        </table>
        {editing && (
          <div className="actions">
            <button onClick={() => setEditing(null)}>취소</button>
            <button
              className="primary"
              disabled={busy}
              onClick={() =>
                run(
                  () =>
                    put<Detail>(`/plans/${id}/draft`, {
                      values: editing.values,
                      validDays: editing.validDays,
                      tags: editing.tags.split(',').map((t) => t.trim()).filter(Boolean),
                    }),
                  () => '저장했습니다 (게시 대기)',
                ).then((d) => {
                  if (d) {
                    setEditing(null)
                    done(d)
                  }
                })
              }
            >
              저장
            </button>
          </div>
        )}
      </section>

      {canApprove && (
        <section className="panel">
          <h2>게시</h2>
          <div className="actions left">
            {draft && state !== 'ENDED' && (
              <>
                <button disabled={busy || missing.length > 0} onClick={() => setScheduling(true)}>
                  게시 예약 📅
                </button>
                <button
                  className="primary"
                  disabled={busy || missing.length > 0}
                  onClick={() => window.confirm('지금 게시합니다. 프런트에 바로 반영됩니다.') && run(() => post<BulkResult>('/plans/publish', { ids: [plan.id] }), (r) => bulkText('게시', r)).then((r) => r && done())}
                >
                  즉시 게시
                </button>
              </>
            )}
            {draft?.state === 'SCHEDULED' && (
              <button disabled={busy} onClick={() => run(() => post<BulkResult>('/plans/cancel-schedule', { ids: [plan.id] }), (r) => bulkText('예약 취소', r)).then((r) => r && done())}>
                예약 취소
              </button>
            )}
            {state !== 'ENDED' && state !== 'HIDDEN' && (
              <button disabled={busy} onClick={() => window.confirm('프런트에서 이 요금제를 숨깁니다.') && run(() => post<Detail>(`/plans/${id}/hide`), () => '비노출로 바꿨습니다').then((d) => d && done(d))}>
                비노출
              </button>
            )}
            {state === 'HIDDEN' && (
              <button disabled={busy} onClick={() => run(() => post<Detail>(`/plans/${id}/unhide`), () => '노출로 바꿨습니다').then((d) => d && done(d))}>
                비노출 해제
              </button>
            )}
            {data.canRollback && (
              <button disabled={busy} onClick={() => window.confirm('직전 게시 버전으로 되돌립니다.') && run(() => post<Detail>(`/plans/${id}/rollback`), () => '직전 버전으로 되돌렸습니다').then((d) => d && done(d))}>
                직전 버전으로 롤백
              </button>
            )}
          </div>
          {state === 'ENDED' && <p className="hint">판매 종료된 요금제입니다. 잘못 종료됐다면 요금제배치관리에서 판매 재개를 요청해 주세요.</p>}
        </section>
      )}

      <section className="panel">
        <h2>버전 이력</h2>
        <table className="grid">
          <thead>
            <tr>
              <th>버전</th>
              <th>상태</th>
              <th>바뀐 항목</th>
              <th>보완</th>
              <th>수집일</th>
              <th>점검 · 승인</th>
              <th>게시</th>
            </tr>
          </thead>
          <tbody>
            {versions.map((v) => (
              <tr key={v.id} className={v.id === plan.publishedVersionId ? 'current' : ''}>
                <td>v{v.versionNo}</td>
                <td>
                  {v.id === plan.publishedVersionId ? '게시 중' : { PUBLISHED: '게시됨', SCHEDULED: '게시 예약', DRAFT: '게시 대기', DISCARDED: '대체됨' }[v.state]}
                </td>
                <td>{v.changedFields.map((f) => FIELD[f] ?? f).join(' · ') || '–'}</td>
                <td>{v.supplementedFields.map((f) => FIELD[f] ?? f).join(' · ') || '–'}</td>
                <td>{v.collectedOn ?? '–'}</td>
                <td>
                  {v.reviewer ?? '–'} / {v.approver ?? '–'} {dateTime(v.approvedAt)}
                </td>
                <td>{v.publishedAt ? `${dateTime(v.publishedAt)} ${v.publishedBy ?? ''}` : v.publishAt ? `📅 ${dateTime(v.publishAt)}` : '–'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {scheduling && (
        <Modal title="게시 예약" onClose={() => setScheduling(false)}>
          <div className="form-line">
            <label>
              게시 일시 <input type="datetime-local" step={600} value={publishAt} onChange={(e) => setPublishAt(e.target.value)} />
            </label>
          </div>
          <p className="hint">10분 단위. 예약 시각까지는 지금 게시 중인 버전이 계속 노출됩니다.</p>
          <div className="actions">
            <button onClick={() => setScheduling(false)}>취소</button>
            <button
              className="primary"
              disabled={busy}
              onClick={() =>
                run(() => post<BulkResult>('/plans/schedule', { ids: [plan.id], publishAt: localToIso(publishAt) }), (r) => bulkText('게시 예약', r)).then((r) => {
                  if (r) {
                    setScheduling(false)
                    done()
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

import { useState } from 'react'
import { Badge, Empty, Loading, Modal, PageHead, Tabs } from '../../components/ui'
import { del, get, post, put } from '../../lib/api'
import { dateTime } from '../../lib/format'
import { useMe } from '../../lib/me'
import { useRun } from '../../lib/notice'
import type { FaqRow } from '../../lib/types'
import { useLoad } from '../../lib/useLoad'

type Form = { id?: number; category: string; question: string; answer: string; sortOrder: number; exposed: boolean }
const CATEGORIES = ['요금제', '개통', '인터넷', '서비스 이용']

/** ST-07 1:1문의관리 — [FAQ] 탭 (1:1 문의는 회원 기능과 함께 추후, 결정 #22) */
export default function Faq() {
  const [tab, setTab] = useState<'FAQ' | 'INQUIRY'>('FAQ')
  return (
    <>
      <PageHead title="1:1문의관리" />
      <Tabs value={tab} onChange={setTab} tabs={[['FAQ', 'FAQ'], ['INQUIRY', '1:1 문의']]} />
      {tab === 'FAQ' ? <FaqTab /> : <Empty>1:1 문의는 회원 로그인 · 마이페이지와 함께 1차 오픈 후 개발합니다 (결정 #22).</Empty>}
    </>
  )
}

function FaqTab() {
  const { can } = useMe()
  const { run, busy } = useRun()
  const { data, error, reload } = useLoad(() => get<FaqRow[]>('/settings/faqs'), [])
  const [form, setForm] = useState<Form | null>(null)
  const canEdit = can('ST-07', 'EDIT')
  const categories = [...new Set([...CATEGORIES, ...(data ?? []).map((f) => f.category)])]

  if (!data) return <Loading error={error} />
  return (
    <>
      <div className="filter-row">
        <span className="muted">노출 중인 질문이 프런트 "자주 묻는 질문"에 분류 · 순서대로 나옵니다.</span>
        {canEdit && (
          <button className="primary" onClick={() => setForm({ category: CATEGORIES[0], question: '', answer: '', sortOrder: data.length + 1, exposed: true })}>
            + 질문 추가
          </button>
        )}
      </div>
      {data.length === 0 ? (
        <Empty>등록된 질문이 없습니다.</Empty>
      ) : (
        <table className="grid">
          <thead>
            <tr>
              <th>분류</th>
              <th className="num">순서</th>
              <th>질문</th>
              <th>노출</th>
              <th>수정</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {data.map((f) => (
              <tr key={f.id}>
                <td>{f.category}</td>
                <td className="num">{f.sortOrder}</td>
                <td>{f.question}</td>
                <td>{f.exposed ? '노출' : <Badge>비노출</Badge>}</td>
                <td className="muted">
                  {f.updatedBy} · {dateTime(f.updatedAt)}
                </td>
                <td className="row-actions">
                  {canEdit && (
                    <>
                      <button onClick={() => setForm({ ...f })}>수정</button>
                      <button onClick={() => window.confirm('이 질문을 삭제합니다.') && run(() => del(`/settings/faqs/${f.id}`), () => '삭제했습니다').then(() => reload())}>삭제</button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {form && (
        <Modal title={form.id ? '질문 수정' : '질문 추가'} onClose={() => setForm(null)}>
          <div className="form-grid">
            <label>
              분류
              <input list="faq-categories" value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} maxLength={30} />
              <datalist id="faq-categories">
                {categories.map((c) => (
                  <option key={c} value={c} />
                ))}
              </datalist>
            </label>
            <label>
              순서 <input type="number" value={form.sortOrder} onChange={(e) => setForm({ ...form, sortOrder: Number(e.target.value) })} />
            </label>
            <label className="inline">
              <input type="checkbox" checked={form.exposed} onChange={(e) => setForm({ ...form, exposed: e.target.checked })} /> 노출
            </label>
            <label className="span2">
              질문 <input value={form.question} onChange={(e) => setForm({ ...form, question: e.target.value })} maxLength={200} />
            </label>
            <label className="span2">
              답변 <textarea rows={8} value={form.answer} onChange={(e) => setForm({ ...form, answer: e.target.value })} maxLength={5000} />
            </label>
          </div>
          <div className="actions">
            <button onClick={() => setForm(null)}>취소</button>
            <button
              className="primary"
              disabled={busy}
              onClick={() =>
                run(() => (form.id ? put(`/settings/faqs/${form.id}`, form) : post('/settings/faqs', form)), () => '저장했습니다').then((r) => {
                  if (r) {
                    setForm(null)
                    void reload()
                  }
                })
              }
            >
              저장
            </button>
          </div>
        </Modal>
      )}
    </>
  )
}

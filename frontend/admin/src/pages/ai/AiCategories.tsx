import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Badge, Loading, Modal, PageHead } from '../../components/ui'
import { get, post, put } from '../../lib/api'
import { useMe } from '../../lib/me'
import { useRun } from '../../lib/notice'
import type { AiCategory } from '../../lib/types'
import { useLoad } from '../../lib/useLoad'

type Form = Omit<AiCategory, 'postCount'> & { isNew: boolean }

/** CT-02 알뜰AI 카테고리관리: 이름 · 주소 · 카드 색 · 순서 · 노출. 글이 있으면 지우지 않고 비노출로 둔다 */
export default function AiCategories() {
  const { can } = useMe()
  const { run, busy } = useRun()
  const { data, error, setData } = useLoad(() => get<AiCategory[]>('/contents/categories'), [])
  const [form, setForm] = useState<Form | null>(null)
  const canEdit = can('CT-02', 'EDIT')

  if (!data) return <Loading error={error} />
  return (
    <>
      <PageHead title="알뜰AI 카테고리관리">
        {canEdit && (
          <button
            className="primary"
            onClick={() =>
              setForm({ isNew: true, code: '', name: '', slug: '', bgColor: '#F1F3F6', accentColor: '#4B5262', sortOrder: data.length + 1, exposed: false })
            }
          >
            + 카테고리 추가
          </button>
        )}
      </PageHead>
      <p className="note">
        노출 중인 카테고리가 프런트 알뜰AI 탭 · 메뉴에 순서대로 나옵니다. 카드 배경 · 강조 색은 목록 카드와 상세 화면에 쓰입니다. 새 카테고리는 비노출로 만들고 글을 채운 뒤 노출해 주세요.
      </p>

      <table className="grid">
        <thead>
          <tr>
            <th className="num">순서</th>
            <th>카드 미리보기</th>
            <th>코드</th>
            <th>주소</th>
            <th className="num">글</th>
            <th>노출</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {data.map((c) => (
            <tr key={c.code}>
              <td className="num">{c.sortOrder}</td>
              <td>
                <span className="ai-cat-swatch" style={{ background: c.bgColor, borderColor: c.accentColor }}>
                  <b style={{ color: c.accentColor }}>{c.name}</b> · 10.15 · 3분 읽기
                </span>
              </td>
              <td>{c.code}</td>
              <td className="muted">/ai/{c.slug}</td>
              <td className="num">
                <Link className="link" to={`/contents/posts?category=${c.code}`}>
                  {c.postCount}
                </Link>
              </td>
              <td>{c.exposed ? '노출' : <Badge>비노출</Badge>}</td>
              <td className="row-actions">{canEdit && <button onClick={() => setForm({ ...c, isNew: false })}>수정</button>}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {form && (
        <Modal title={form.isNew ? '카테고리 추가' : '카테고리 수정'} onClose={() => setForm(null)}>
          <div className="form-grid">
            <label>
              코드
              <input
                value={form.code}
                onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })}
                maxLength={20}
                placeholder="예: EVENT"
                disabled={!form.isNew}
              />
            </label>
            <label>
              이름 <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} maxLength={30} />
            </label>
            <label>
              주소 (/ai/…)
              <input value={form.slug} onChange={(e) => setForm({ ...form, slug: e.target.value.toLowerCase() })} maxLength={40} placeholder="예: event" />
            </label>
            <label>
              카드 배경 <input type="color" value={form.bgColor} onChange={(e) => setForm({ ...form, bgColor: e.target.value.toUpperCase() })} />
            </label>
            <label>
              강조 색 <input type="color" value={form.accentColor} onChange={(e) => setForm({ ...form, accentColor: e.target.value.toUpperCase() })} />
            </label>
            <label>
              미리보기
              <span className="ai-cat-swatch" style={{ background: form.bgColor, borderColor: form.accentColor }}>
                <b style={{ color: form.accentColor }}>{form.name || '이름'}</b> · 10.15 · 3분 읽기
              </span>
            </label>
            <label>
              순서 <input type="number" value={form.sortOrder} onChange={(e) => setForm({ ...form, sortOrder: Number(e.target.value) })} />
            </label>
            <label className="inline">
              <input type="checkbox" checked={form.exposed} onChange={(e) => setForm({ ...form, exposed: e.target.checked })} /> 노출
            </label>
          </div>
          <div className="actions">
            <button onClick={() => setForm(null)}>취소</button>
            <button
              className="primary"
              disabled={busy}
              onClick={() =>
                run(
                  () => (form.isNew ? post<AiCategory[]>('/contents/categories', form) : put<AiCategory[]>(`/contents/categories/${form.code}`, form)),
                  () => '저장했습니다',
                ).then((r) => {
                  if (r) {
                    setData(r)
                    setForm(null)
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

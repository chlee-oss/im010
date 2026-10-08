import { useState } from 'react'
import { Badge, Empty, Loading, Modal, PageHead, Tabs } from '../../components/ui'
import { del, get, post, put } from '../../lib/api'
import { TERMS_TYPE, dateTime, today } from '../../lib/format'
import { useMe } from '../../lib/me'
import { useRun } from '../../lib/notice'
import type { TermsRow, TermsType } from '../../lib/types'
import { useLoad } from '../../lib/useLoad'

type Form = { id?: number; version: string; body: string; effectiveOn: string }

/** ST-04 약관관리: 종류별 버전 · 시행일 · 게시. 게시한 버전은 고치지 않고 새 버전을 만든다. */
export default function Terms() {
  const { can } = useMe()
  const { run, busy } = useRun()
  const [type, setType] = useState<TermsType>('SERVICE')
  const { data, error, reload } = useLoad(() => get<TermsRow[]>(`/settings/terms/${type}`), [type])
  const [form, setForm] = useState<Form | null>(null)
  const [view, setView] = useState<TermsRow | null>(null)
  const canEdit = can('ST-04', 'EDIT')
  const t = today()
  const current = data?.filter((x) => x.status === 'PUBLISHED' && x.effectiveOn <= t)[0]

  const save = () =>
    form &&
    run(() => (form.id ? put<TermsRow>(`/settings/terms/item/${form.id}`, form) : post<TermsRow>(`/settings/terms/${type}`, form)), () => '저장했습니다 (게시 전)').then((r) => {
      if (r) {
        setForm(null)
        void reload()
      }
    })

  return (
    <>
      <PageHead title="약관관리">
        {canEdit && (
          <button className="primary" onClick={() => setForm({ version: '', body: data?.[0]?.body ?? '', effectiveOn: t })}>
            + 새 버전
          </button>
        )}
      </PageHead>
      <Tabs value={type} onChange={setType} tabs={Object.entries(TERMS_TYPE).map(([k, v]) => [k as TermsType, v])} />
      <p className="note">
        게시한 버전은 고칠 수 없고, 시행일이 지난 가장 최근 게시 버전이 프런트에 노출됩니다 (이전 버전도 프런트에서 볼 수 있음). 이용약관 · 개인정보처리방침은 Footer 링크로 연결됩니다.
        개정 공지(시행 7일 전 등) 방식은 정의 필요 항목입니다.
      </p>
      {!data ? (
        <Loading error={error} />
      ) : data.length === 0 ? (
        <Empty>등록된 버전이 없습니다.</Empty>
      ) : (
        <table className="grid">
          <thead>
            <tr>
              <th>버전</th>
              <th>시행일</th>
              <th>상태</th>
              <th>작성</th>
              <th>게시</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {data.map((x) => (
              <tr key={x.id} className={x.id === current?.id ? 'current' : ''}>
                <td>{x.version}</td>
                <td>{x.effectiveOn}</td>
                <td>
                  {x.status === 'DRAFT' ? (
                    <Badge tone="amber">작성 중</Badge>
                  ) : x.id === current?.id ? (
                    <Badge tone="green">시행 중</Badge>
                  ) : x.effectiveOn > t ? (
                    <Badge tone="navy">시행 예정</Badge>
                  ) : (
                    <Badge>이전 버전</Badge>
                  )}
                </td>
                <td>
                  {x.createdBy} · {dateTime(x.createdAt)}
                </td>
                <td>{x.publishedAt ? `${x.publishedBy} · ${dateTime(x.publishedAt)}` : '–'}</td>
                <td className="row-actions">
                  <button onClick={() => setView(x)}>보기</button>
                  {canEdit && x.status === 'DRAFT' && (
                    <>
                      <button onClick={() => setForm({ id: x.id, version: x.version, body: x.body, effectiveOn: x.effectiveOn })}>수정</button>
                      <button onClick={() => window.confirm('작성 중인 버전을 삭제합니다.') && run(() => del(`/settings/terms/item/${x.id}`), () => '삭제했습니다').then(() => reload())}>삭제</button>
                      <button
                        className="primary"
                        disabled={busy}
                        onClick={() =>
                          window.confirm(`${x.version} 버전을 게시합니다. 시행일(${x.effectiveOn})부터 프런트에 노출되며 게시 후에는 고칠 수 없습니다.`) &&
                          run(() => post(`/settings/terms/item/${x.id}/publish`), () => '게시했습니다').then(() => reload())
                        }
                      >
                        게시
                      </button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {form && (
        <Modal title={`${TERMS_TYPE[type]} ${form.id ? '수정' : '새 버전'}`} onClose={() => setForm(null)}>
          <div className="form-grid">
            <label>
              버전 <input value={form.version} onChange={(e) => setForm({ ...form, version: e.target.value })} placeholder="예: v1.1" maxLength={20} />
            </label>
            <label>
              시행일 <input type="date" value={form.effectiveOn} onChange={(e) => setForm({ ...form, effectiveOn: e.target.value })} />
            </label>
            <label className="span2">
              본문
              <textarea rows={16} value={form.body} onChange={(e) => setForm({ ...form, body: e.target.value })} />
            </label>
          </div>
          <div className="actions">
            <button onClick={() => setForm(null)}>취소</button>
            <button className="primary" disabled={busy} onClick={() => void save()}>
              저장 (게시 전)
            </button>
          </div>
        </Modal>
      )}
      {view && (
        <Modal title={`${TERMS_TYPE[view.termsType]} ${view.version}`} onClose={() => setView(null)}>
          <p className="muted">시행일 {view.effectiveOn}</p>
          <div className="doc-preview">{view.body}</div>
          <div className="actions">
            <button onClick={() => setView(null)}>닫기</button>
          </div>
        </Modal>
      )}
    </>
  )
}

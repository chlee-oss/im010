import { useState } from 'react'
import { Loading, PageHead } from '../../components/ui'
import { get, post } from '../../lib/api'
import { dateTime } from '../../lib/format'
import { useMe } from '../../lib/me'
import { useRun } from '../../lib/notice'
import type { FooterRow } from '../../lib/types'
import { useLoad } from '../../lib/useLoad'

type Form = Omit<FooterRow, 'id' | 'createdBy' | 'createdAt'>

const FIELDS: [keyof Form, string, number][] = [
  ['companyName', '상호', 100],
  ['ceo', '대표', 50],
  ['businessNo', '사업자등록번호', 20],
  ['mailOrderNo', '통신판매업신고', 50],
  ['address', '주소', 200],
  ['csPhone', '고객센터', 30],
  ['csHours', '운영 시간', 100],
  ['email', '이메일', 100],
]

/** ST-06 Footer관리: 사업자 정보 · 고객센터 · 하단 고지. 저장하면 바로 프런트에 반영되고 이전 값은 이력으로 남는다. */
export default function Footer() {
  const { can } = useMe()
  const { run, busy } = useRun()
  const { data, error, setData } = useLoad(() => get<FooterRow[]>('/settings/footer'), [])
  const [form, setForm] = useState<Form | null>(null)

  if (!data) return <Loading error={error} />
  const latest = data[0]
  const v: Form | null = form ?? latest ?? null
  return (
    <>
      <PageHead title="Footer관리">
        {can('ST-06', 'EDIT') && !form && latest && <button onClick={() => setForm({ ...latest })}>수정</button>}
      </PageHead>
      <p className="note">저장하면 바로 프런트 하단에 반영됩니다. 이용약관 · 개인정보처리방침 링크는 약관관리에서 게시한 버전으로 자동 연결됩니다.</p>
      {v && (
        <section className="panel">
          <div className="form-grid">
            {FIELDS.map(([k, label, max]) => (
              <label key={k} className={k === 'address' ? 'span2' : ''}>
                {label}
                <input disabled={!form} value={(v[k] as string | null) ?? ''} onChange={(e) => form && setForm({ ...form, [k]: e.target.value })} maxLength={max} />
              </label>
            ))}
            <label className="span2">
              하단 고지 문구
              <textarea rows={4} disabled={!form} value={v.notice ?? ''} onChange={(e) => form && setForm({ ...form, notice: e.target.value })} maxLength={2000} />
            </label>
          </div>
          {form && (
            <div className="actions">
              <button onClick={() => setForm(null)}>취소</button>
              <button
                className="primary"
                disabled={busy}
                onClick={() =>
                  run(() => post<FooterRow[]>('/settings/footer', form), () => '저장했습니다 — 프런트에 반영됩니다').then((r) => {
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
          )}
        </section>
      )}
      <section className="panel">
        <h2>변경 이력</h2>
        <table className="grid">
          <thead>
            <tr>
              <th>저장 일시</th>
              <th>저장자</th>
              <th>상호</th>
              <th>대표</th>
              <th>고객센터</th>
              <th>이메일</th>
            </tr>
          </thead>
          <tbody>
            {data.map((r, i) => (
              <tr key={r.id} className={i === 0 ? 'current' : ''}>
                <td>{dateTime(r.createdAt)}</td>
                <td>{r.createdBy}</td>
                <td>{r.companyName}</td>
                <td>{r.ceo}</td>
                <td>{r.csPhone}</td>
                <td>{r.email}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </>
  )
}

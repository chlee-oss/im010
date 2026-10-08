import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Badge, Loading, Modal, PageHead } from '../components/ui'
import { get, post } from '../lib/api'
import { DAYS, RUN_RESULT, dateTime } from '../lib/format'
import { useMe } from '../lib/me'
import { useRun } from '../lib/notice'
import type { Partner } from '../lib/types'
import { useLoad } from '../lib/useLoad'

export function scheduleText(p: Partner): string {
  if (!p.scheduleEnabled) return 'OFF'
  const days = p.scheduleDays === 'DAILY' ? '매일' : (p.scheduleDays ?? '').split(',').map((d) => DAYS.find(([k]) => k === d)?.[1] ?? d).join('·')
  return `${days} ${(p.scheduleTime ?? '').slice(0, 5)}`
}

export function count(n: number) {
  return n > 0 ? <span>● {n}개</span> : <span className="muted">– 미등록</span>
}

/** PA-01 제휴사관리 [알뜰폰] 목록 */
export default function Partners() {
  const navigate = useNavigate()
  const { can } = useMe()
  const { run, busy } = useRun()
  const { data, error } = useLoad(() => get<Partner[]>('/partners'), [])
  const [creating, setCreating] = useState(false)
  const [form, setForm] = useState({ code: '', name: '', chipBg: '#FFF1EE', chipFg: '#D9432F', homepageUrl: 'https://', exposed: false, sortOrder: 99 })

  return (
    <>
      <PageHead title="제휴사관리">
        {can('PA-01', 'EDIT') && (
          <button className="primary" onClick={() => setCreating(true)}>
            + 제휴사 추가
          </button>
        )}
      </PageHead>
      <p className="note">[알뜰폰] 탭 · 인터넷 제휴업체([인터넷] 탭)는 다음 단계(3-2)에서 만듭니다.</p>
      {!data ? (
        <Loading error={error} />
      ) : (
        <table className="grid">
          <thead>
            <tr>
              <th>제휴사</th>
              <th>후불 URL</th>
              <th>선불 URL</th>
              <th>이달의 URL</th>
              <th>사이트 탭</th>
              <th>노출</th>
              <th>수집 스케줄</th>
              <th>최근 수집</th>
            </tr>
          </thead>
          <tbody>
            {data.map((p) => (
              <tr key={p.code} className="clickable" onClick={() => navigate(`/partners/${p.code}`)}>
                <td>
                  <span className="chip" style={{ background: p.chipBg, color: p.chipFg }}>
                    {p.name}
                  </span>{' '}
                  <span className="muted">{p.code}</span>
                </td>
                <td>{count(p.postpaidUrls)}</td>
                <td>{count(p.prepaidUrls)}</td>
                <td>{count(p.monthlyUrls)}</td>
                <td>{p.newTabs > 0 ? <Badge tone="amber">⚠ 미확인 {p.newTabs}</Badge> : <span className="muted">–</span>}</td>
                <td>{p.exposed ? '노출' : <Badge>비노출</Badge>}</td>
                <td>{scheduleText(p)}</td>
                <td>
                  {p.lastRunResult ? (
                    <>
                      {dateTime(p.lastRunAt)}{' '}
                      {p.lastRunResult !== 'SUCCESS' && <Badge tone="red">⚠ {RUN_RESULT[p.lastRunResult]}</Badge>}
                    </>
                  ) : (
                    <span className="muted">–</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {creating && (
        <Modal title="제휴사 추가" onClose={() => setCreating(false)}>
          <div className="form-grid">
            <label>
              코드 <input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value.toLowerCase() })} placeholder="영문 소문자 · 숫자 2~10자" maxLength={10} />
            </label>
            <label>
              브랜드명 <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} maxLength={50} />
            </label>
            <label>
              홈페이지 <input value={form.homepageUrl} onChange={(e) => setForm({ ...form, homepageUrl: e.target.value })} maxLength={500} />
            </label>
            <label>
              칩 배경 <input type="color" value={form.chipBg} onChange={(e) => setForm({ ...form, chipBg: e.target.value })} />
            </label>
            <label>
              칩 글자 <input type="color" value={form.chipFg} onChange={(e) => setForm({ ...form, chipFg: e.target.value })} />
            </label>
          </div>
          <p className="hint">처음에는 비노출 · 스케줄 OFF로 만들어집니다. 수집 URL을 등록하고 [테스트]로 확인한 뒤 켜 주세요.</p>
          <div className="actions">
            <button onClick={() => setCreating(false)}>취소</button>
            <button className="primary" disabled={busy} onClick={() => run(() => post('/partners', form), () => '제휴사를 추가했습니다').then((r) => r && navigate(`/partners/${form.code}`))}>
              추가
            </button>
          </div>
        </Modal>
      )}
    </>
  )
}

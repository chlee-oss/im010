import { useState } from 'react'
import { Empty, Loading, PageHead, Pager } from '../../components/ui'
import { get, qs } from '../../lib/api'
import { AUDIT_KIND, fullDateTime, today } from '../../lib/format'
import { useMe } from '../../lib/me'
import type { AuditRow } from '../../lib/types'
import { useLoad } from '../../lib/useLoad'

/** ST-05 접속이력관리: 로그인 · 처리 이력 조회 · 다운로드 (수정 · 삭제 없음) */
export default function Audit() {
  const { can } = useMe()
  const [from, setFrom] = useState('')
  const [to, setTo] = useState(today())
  const [kind, setKind] = useState('')
  const [loginId, setLoginId] = useState('')
  const [action, setAction] = useState('')
  const [page, setPage] = useState(1)
  const params = { from, to, kind, loginId: loginId.trim(), action: action.trim() }
  const { data, error } = useLoad(() => get<{ total: number; page: number; items: AuditRow[] }>('/settings/audits' + qs({ ...params, page })), [from, to, kind, loginId, action, page])

  return (
    <>
      <PageHead title="접속이력관리">
        {can('ST-05', 'DOWNLOAD') && (
          <a className="button" href={'/admin/api/settings/audits.csv' + qs(params)}>
            엑셀 다운로드
          </a>
        )}
      </PageHead>
      <div className="filter-row">
        <label>
          기간 <input type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(1) }} /> ~ <input type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(1) }} />
        </label>
        <label>
          구분{' '}
          <select value={kind} onChange={(e) => { setKind(e.target.value); setPage(1) }}>
            <option value="">전체</option>
            {Object.entries(AUDIT_KIND).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </label>
        <label>
          관리자 <input value={loginId} onChange={(e) => { setLoginId(e.target.value); setPage(1) }} placeholder="아이디" maxLength={50} style={{ width: 120 }} />
        </label>
        <label>
          동작 <input value={action} onChange={(e) => { setAction(e.target.value.toUpperCase()); setPage(1) }} placeholder="예: APPROVE" maxLength={40} style={{ width: 140 }} />
        </label>
        <span className="muted">기간을 비우면 최근 7일 · 다운로드도 기록됩니다</span>
      </div>
      {!data ? (
        <Loading error={error} />
      ) : data.items.length === 0 ? (
        <Empty>이력이 없습니다.</Empty>
      ) : (
        <table className="grid">
          <thead>
            <tr>
              <th>일시</th>
              <th>구분</th>
              <th>관리자</th>
              <th>프로그램</th>
              <th>동작</th>
              <th>대상</th>
              <th>내용</th>
              <th>IP</th>
            </tr>
          </thead>
          <tbody>
            {data.items.map((r) => (
              <tr key={r.id}>
                <td>{fullDateTime(r.createdAt)}</td>
                <td>{AUDIT_KIND[r.kind]}</td>
                <td>{r.loginId ?? '–'}</td>
                <td>{r.programId ?? '–'}</td>
                <td>{r.action}</td>
                <td className="wrap-cell">{r.target ?? ''}</td>
                <td className="wrap-cell muted">{r.detail ?? ''}</td>
                <td className="muted">{r.ip ?? ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {data && <Pager page={data.page} total={data.total} size={100} onChange={setPage} />}
    </>
  )
}

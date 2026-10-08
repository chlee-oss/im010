import { useState } from 'react'
import { Badge, Empty, Loading, PageHead, Pager } from '../components/ui'
import { get, qs } from '../lib/api'
import { RECEIPT_CATEGORY, RECEIPT_RESULT, fullDateTime, today } from '../lib/format'
import { useMe } from '../lib/me'
import type { ReceiptPage } from '../lib/types'
import { useLoad } from '../lib/useLoad'

const CONFIG = {
  plans: {
    program: 'RC-01',
    title: '알뜰폰접수신청',
    prefix: 'M-',
    note: '요금제 상세(S2)에서 `개통하기`를 누를 때마다 1건 접수됩니다. 개통 신청서는 제휴사 사이트에서 작성하므로 개인정보는 없습니다. 실제 개통 여부는 제휴사 정산 자료와 대조합니다.',
    categories: [['POSTPAID', '후불'], ['PREPAID', '선불']],
    partnerLabel: '제휴사',
  },
  internet: {
    program: 'RC-02',
    title: '인터넷접수신청',
    prefix: 'I-',
    note: '메인 인터넷 상품의 `상담 신청`으로 제휴업체 신청 페이지에 이동할 때마다 1건 기록됩니다. 상담 신청서는 제휴업체 페이지에서 작성하므로 개인정보는 없습니다.',
    categories: [['SINGLE', '단독'], ['BUNDLE', '결합']],
    partnerLabel: '제휴업체',
  },
} as const

/** RC-01 알뜰폰접수신청 · RC-02 인터넷접수신청 */
export default function Receipts({ kind }: { kind: 'plans' | 'internet' }) {
  const cfg = CONFIG[kind]
  const { can } = useMe()
  const [from, setFrom] = useState('')
  const [to, setTo] = useState(today())
  const [partner, setPartner] = useState('')
  const [category, setCategory] = useState('')
  const [fromPage, setFromPage] = useState('')
  const [result, setResult] = useState('')
  const [page, setPage] = useState(1)
  const params = { from, to, partner, category, fromPage: fromPage.trim(), result }
  const { data, error } = useLoad(() => get<ReceiptPage>(`/receipts/${kind}` + qs({ ...params, page })), [kind, from, to, partner, category, fromPage, result, page])
  const set = (fn: (v: string) => void) => (v: string) => {
    fn(v)
    setPage(1)
  }

  return (
    <>
      <PageHead title={cfg.title}>
        {can(cfg.program, 'DOWNLOAD') && (
          <a className="button" href={`/admin/api/receipts/${kind}.csv` + qs(params)}>
            엑셀 다운로드
          </a>
        )}
      </PageHead>
      <p className="note">{cfg.note}</p>
      <div className="filter-row">
        <label>
          기간 <input type="date" value={from} onChange={(e) => set(setFrom)(e.target.value)} /> ~ <input type="date" value={to} onChange={(e) => set(setTo)(e.target.value)} />
        </label>
        <label>
          {cfg.partnerLabel}{' '}
          <select value={partner} onChange={(e) => set(setPartner)(e.target.value)}>
            <option value="">전체</option>
            {(data?.byPartner ?? []).filter((p) => p.key).map((p) => (
              <option key={p.key} value={p.key!}>
                {p.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          구분{' '}
          <select value={category} onChange={(e) => set(setCategory)(e.target.value)}>
            <option value="">전체</option>
            {cfg.categories.map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </label>
        <label>
          이동 결과{' '}
          <select value={result} onChange={(e) => set(setResult)(e.target.value)}>
            <option value="">전체</option>
            {Object.entries(RECEIPT_RESULT).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </label>
        <label>
          유입 화면 <input value={fromPage} onChange={(e) => set(setFromPage)(e.target.value)} placeholder="예: S2" style={{ width: 90 }} maxLength={30} />
        </label>
      </div>
      {data && (
        <div className="summary-row">
          <div className="stat">
            <span>오늘</span>
            <b>{data.today.toLocaleString()}건</b>
          </div>
          <div className="stat">
            <span>조회 기간</span>
            <b>{data.periodTotal.toLocaleString()}건</b>
          </div>
          <div className="stat wide">
            <span>{cfg.partnerLabel}별</span>
            <div className="chips">
              {data.byPartner.map((p) => (
                <span key={p.key ?? 'none'} className="mini">
                  {p.label} <b>{p.count.toLocaleString()}</b>
                </span>
              ))}
              {data.byPartner.length === 0 && <span className="muted">–</span>}
            </div>
          </div>
        </div>
      )}
      {!data ? (
        <Loading error={error} />
      ) : data.items.length === 0 ? (
        <Empty>접수 내역이 없습니다.</Empty>
      ) : (
        <table className="grid">
          <thead>
            <tr>
              <th>접수번호</th>
              <th>{kind === 'plans' ? '접수 일시' : '이동 일시'}</th>
              {kind === 'internet' && <th>통신사</th>}
              <th>{cfg.partnerLabel}</th>
              <th>구분</th>
              <th>{kind === 'plans' ? '요금제' : '상품'}</th>
              <th>유입 화면</th>
              <th>이동 결과</th>
            </tr>
          </thead>
          <tbody>
            {data.items.map((r) => (
              <tr key={r.id}>
                <td>
                  {cfg.prefix}
                  {r.id}
                </td>
                <td>{fullDateTime(r.createdAt)}</td>
                {kind === 'internet' && <td>{r.carrier === 'LGU' ? 'LG U+' : r.carrier ?? '–'}</td>}
                <td>{r.partnerName ?? '–'}</td>
                <td>{r.category ? RECEIPT_CATEGORY[r.category] ?? r.category : '–'}</td>
                <td>
                  {r.targetName ?? `#${r.targetId}`} {r.versionNo && <span className="muted">v{r.versionNo}</span>}
                </td>
                <td>{r.fromPage ?? '–'}</td>
                <td>{r.result === 'FORWARDED' ? '이동 완료' : <Badge tone="amber">⚠ {RECEIPT_RESULT[r.result]}</Badge>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {data && <Pager page={data.page} total={data.total} size={100} onChange={setPage} />}
    </>
  )
}

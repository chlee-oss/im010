import { useState } from 'react'
import { Badge, Empty, Loading, Modal } from '../components/ui'
import { get, post, put } from '../lib/api'
import { NETWORK } from '../lib/format'
import { useMe } from '../lib/me'
import { useNotify, useRun } from '../lib/notice'
import type { InternetPartner } from '../lib/types'
import { useLoad } from '../lib/useLoad'

type Form = Omit<InternetPartner, 'id' | 'productCount'> & { id?: number }

const EMPTY: Form = {
  name: '',
  carrier: 'SKT',
  applyUrl: 'https://',
  status: 'ACTIVE',
  businessNo: '',
  contactName: '',
  contactPhone: '',
  contractStart: null,
  contractEnd: null,
  memo: '',
}

/** PA-01 제휴사관리 [인터넷]: 인터넷 신청을 넘겨받는 제휴업체와 기본 신청 페이지 URL (결정 #18) */
export default function InternetPartners() {
  const { can } = useMe()
  const notify = useNotify()
  const { run, busy } = useRun()
  const { data, error, reload } = useLoad(() => get<InternetPartner[]>('/internet/partners'), [])
  const [form, setForm] = useState<Form | null>(null)
  const canEdit = can('PA-01', 'EDIT')

  return (
    <>
      <div className="filter-row">
        <span className="muted">계약 종료일이 지나면 자동으로 "종료"가 되고, 연결된 상품의 신청 버튼은 동작하지 않습니다 → 인터넷관리에서 다른 업체로 바꿔 주세요.</span>
        {canEdit && (
          <button className="primary" onClick={() => setForm({ ...EMPTY })}>
            + 제휴업체 추가
          </button>
        )}
      </div>
      {!data ? (
        <Loading error={error} />
      ) : data.length === 0 ? (
        <Empty>등록된 제휴업체가 없습니다.</Empty>
      ) : (
        <table className="grid">
          <thead>
            <tr>
              <th>업체명</th>
              <th>통신사</th>
              <th>신청 페이지 URL (기본)</th>
              <th>담당자</th>
              <th>계약 기간</th>
              <th className="num">상품</th>
              <th>상태</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {data.map((p) => (
              <tr key={p.id}>
                <td>{p.name}</td>
                <td>{NETWORK[p.carrier]}</td>
                <td className="url">
                  {p.applyUrl ? (
                    <a className="link" href={p.applyUrl} target="_blank" rel="noopener noreferrer">
                      {p.applyUrl} ↗
                    </a>
                  ) : (
                    <Badge tone="amber">⚠ 없음</Badge>
                  )}
                </td>
                <td>
                  {p.contactName ?? '–'} <span className="muted">{p.contactPhone ?? ''}</span>
                </td>
                <td>{p.contractStart || p.contractEnd ? `${p.contractStart ?? ''} ~ ${p.contractEnd ?? ''}` : '–'}</td>
                <td className="num">{p.productCount}</td>
                <td>{p.status === 'ACTIVE' ? '계약 중' : <Badge tone="red">종료</Badge>}</td>
                <td className="row-actions">{canEdit && <button onClick={() => setForm({ ...p })}>수정</button>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {form && (
        <Modal title={form.id ? '제휴업체 수정' : '제휴업체 추가'} onClose={() => setForm(null)}>
          <div className="form-grid">
            <label>
              업체명 <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} maxLength={100} />
            </label>
            <label>
              통신사
              <select value={form.carrier} onChange={(e) => setForm({ ...form, carrier: e.target.value })}>
                {Object.entries(NETWORK).map(([k, v]) => (
                  <option key={k} value={k}>
                    {v}
                  </option>
                ))}
              </select>
            </label>
            <label>
              상태
              <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value as Form['status'] })}>
                <option value="ACTIVE">계약 중</option>
                <option value="ENDED">종료</option>
              </select>
            </label>
            <label className="span2">
              신청 페이지 URL (기본) <input value={form.applyUrl ?? ''} onChange={(e) => setForm({ ...form, applyUrl: e.target.value })} maxLength={500} />
            </label>
            <label>
              사업자등록번호 <input value={form.businessNo ?? ''} onChange={(e) => setForm({ ...form, businessNo: e.target.value })} maxLength={20} />
            </label>
            <label>
              담당자 <input value={form.contactName ?? ''} onChange={(e) => setForm({ ...form, contactName: e.target.value })} maxLength={50} />
            </label>
            <label>
              연락처 <input value={form.contactPhone ?? ''} onChange={(e) => setForm({ ...form, contactPhone: e.target.value })} maxLength={30} />
            </label>
            <label>
              계약 시작 <input type="date" value={form.contractStart ?? ''} onChange={(e) => setForm({ ...form, contractStart: e.target.value || null })} />
            </label>
            <label>
              계약 종료 <input type="date" value={form.contractEnd ?? ''} onChange={(e) => setForm({ ...form, contractEnd: e.target.value || null })} />
            </label>
            <label className="span2">
              메모 <input value={form.memo ?? ''} onChange={(e) => setForm({ ...form, memo: e.target.value })} maxLength={500} />
            </label>
          </div>
          <div className="actions">
            <button onClick={() => setForm(null)}>취소</button>
            <button
              className="primary"
              disabled={busy}
              onClick={() =>
                run(() => (form.id ? put<{ warning: string | null }>(`/internet/partners/${form.id}`, form) : post<{ warning: string | null }>('/internet/partners', form)), () => '저장했습니다').then((r) => {
                  if (r) {
                    if (r.warning) notify('error', r.warning)
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

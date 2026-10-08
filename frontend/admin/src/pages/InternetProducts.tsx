import { useState } from 'react'
import { Badge, Empty, Loading, Modal, PageHead, Tabs } from '../components/ui'
import { get, post, put } from '../lib/api'
import { NETWORK, won } from '../lib/format'
import { useMe } from '../lib/me'
import { useRun } from '../lib/notice'
import type { InternetPartner, InternetProduct } from '../lib/types'
import { useLoad } from '../lib/useLoad'

type Form = {
  id?: number
  carrier: string
  productType: 'SINGLE' | 'BUNDLE'
  name: string
  monthlyPrice: number | ''
  benefits: string
  internetPartnerId: number | ''
  applyUrl: string
  sortOrder: number
  exposed: boolean
}

/** PR-02 인터넷관리: 인터넷 상품 (단독 · 결합) — 수집하지 않고 직접 등록 (결정 #15) */
export default function InternetProducts() {
  const { can } = useMe()
  const { run, busy } = useRun()
  const [type, setType] = useState<'SINGLE' | 'BUNDLE'>('SINGLE')
  const { data, error, reload } = useLoad(() => get<InternetProduct[]>('/internet/products?type=' + type), [type])
  const partners = useLoad(() => get<InternetPartner[]>('/internet/partner-options'), [])
  const [form, setForm] = useState<Form | null>(null)
  const canEdit = can('PR-02', 'EDIT')

  const save = () =>
    form &&
    run(
      () => {
        const body = {
          ...form,
          monthlyPrice: form.monthlyPrice === '' ? null : form.monthlyPrice,
          internetPartnerId: form.internetPartnerId === '' ? null : form.internetPartnerId,
          benefits: form.benefits.split('\n').map((b) => b.trim()).filter(Boolean),
        }
        return form.id ? put(`/internet/products/${form.id}`, body) : post('/internet/products', body)
      },
      () => '저장했습니다',
    ).then((r) => {
      if (r) {
        setForm(null)
        void reload()
      }
    })

  return (
    <>
      <PageHead title="인터넷관리">
        {canEdit && (
          <button
            className="primary"
            onClick={() => setForm({ carrier: 'SKT', productType: type, name: '', monthlyPrice: '', benefits: '', internetPartnerId: '', applyUrl: '', sortOrder: (data?.length ?? 0) + 1, exposed: true })}
          >
            + 상품 추가
          </button>
        )}
      </PageHead>
      <Tabs value={type} onChange={setType} tabs={[['SINGLE', '단독'], ['BUNDLE', '결합 (인터넷 + TV)']]} />
      <p className="note">
        프런트 `상담 신청`은 im010 이동 주소를 거쳐 상품별 신청 URL로, 비어 있으면 담당 업체의 기본 신청 페이지로 보냅니다 (RC-02 기록). 담당 업체는 제휴사관리 [인터넷]에서 관리합니다.
      </p>
      {!data ? (
        <Loading error={error} />
      ) : data.length === 0 ? (
        <Empty>등록된 상품이 없습니다.</Empty>
      ) : (
        <table className="grid">
          <thead>
            <tr>
              <th className="num">순서</th>
              <th>통신사</th>
              <th>상품</th>
              <th className="num">월 요금</th>
              <th>혜택</th>
              <th>담당 업체 · 신청 URL</th>
              <th>노출</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {data.map((p) => {
              const url = p.applyUrl ?? p.partnerApplyUrl
              const broken = !url || p.partnerStatus === 'ENDED'
              return (
                <tr key={p.id}>
                  <td className="num">{p.sortOrder}</td>
                  <td>{NETWORK[p.carrier]}</td>
                  <td>{p.name}</td>
                  <td className="num">{won(p.monthlyPrice)}</td>
                  <td className="muted">{p.benefits.split('|').filter(Boolean).join(' · ')}</td>
                  <td>
                    {p.partnerName ?? '–'} {p.partnerStatus === 'ENDED' && <Badge tone="red">계약 종료</Badge>}
                    <div className="muted small">{p.applyUrl ? '상품 URL' : p.partnerApplyUrl ? '업체 기본 URL' : ''}</div>
                    {broken && p.exposed && <Badge tone="amber">⚠ 신청 버튼이 동작하지 않음</Badge>}
                  </td>
                  <td>{p.exposed ? '노출' : <Badge>비노출</Badge>}</td>
                  <td className="row-actions">
                    {canEdit && (
                      <button
                        onClick={() =>
                          setForm({
                            id: p.id,
                            carrier: p.carrier,
                            productType: p.productType,
                            name: p.name,
                            monthlyPrice: p.monthlyPrice,
                            benefits: p.benefits.split('|').filter(Boolean).join('\n'),
                            internetPartnerId: p.internetPartnerId ?? '',
                            applyUrl: p.applyUrl ?? '',
                            sortOrder: p.sortOrder,
                            exposed: p.exposed,
                          })
                        }
                      >
                        수정
                      </button>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      )}
      {form && (
        <Modal title={form.id ? '상품 수정' : '상품 추가'} onClose={() => setForm(null)}>
          <div className="form-grid">
            <label>
              통신사
              <select value={form.carrier} onChange={(e) => setForm({ ...form, carrier: e.target.value, internetPartnerId: '' })}>
                {Object.entries(NETWORK).map(([k, v]) => (
                  <option key={k} value={k}>
                    {v}
                  </option>
                ))}
              </select>
            </label>
            <label>
              유형
              <select value={form.productType} onChange={(e) => setForm({ ...form, productType: e.target.value as Form['productType'] })}>
                <option value="SINGLE">단독</option>
                <option value="BUNDLE">결합</option>
              </select>
            </label>
            <label>
              순서 <input type="number" value={form.sortOrder} onChange={(e) => setForm({ ...form, sortOrder: Number(e.target.value) })} />
            </label>
            <label className="span2">
              상품명 <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} maxLength={100} />
            </label>
            <label>
              월 요금 <input type="number" min={0} value={form.monthlyPrice} onChange={(e) => setForm({ ...form, monthlyPrice: e.target.value === '' ? '' : Number(e.target.value) })} />
            </label>
            <label>
              담당 업체
              <select value={form.internetPartnerId} onChange={(e) => setForm({ ...form, internetPartnerId: e.target.value ? Number(e.target.value) : '' })}>
                <option value="">없음</option>
                {(partners.data ?? [])
                  .filter((p) => p.carrier === form.carrier)
                  .map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                      {p.status === 'ENDED' ? ' (계약 종료)' : ''}
                    </option>
                  ))}
              </select>
            </label>
            <label className="span2">
              상품별 신청 URL (비우면 업체 기본 URL) <input value={form.applyUrl} onChange={(e) => setForm({ ...form, applyUrl: e.target.value })} placeholder="https://" maxLength={500} />
            </label>
            <label className="span2">
              혜택 (한 줄에 하나, 최대 5개) <textarea rows={4} value={form.benefits} onChange={(e) => setForm({ ...form, benefits: e.target.value })} />
            </label>
            <label className="inline">
              <input type="checkbox" checked={form.exposed} onChange={(e) => setForm({ ...form, exposed: e.target.checked })} /> 프런트 노출
            </label>
          </div>
          <div className="actions">
            <button onClick={() => setForm(null)}>취소</button>
            <button className="primary" disabled={busy} onClick={() => void save()}>
              저장
            </button>
          </div>
        </Modal>
      )}
    </>
  )
}

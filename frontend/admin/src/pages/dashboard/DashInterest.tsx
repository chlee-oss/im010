import { useMemo, useState } from 'react'
import { Loading, PageHead } from '../../components/ui'
import { get } from '../../lib/api'
import { NETWORK, won } from '../../lib/format'
import { useMe } from '../../lib/me'
import type { DashInterest as Data, DashPlan } from '../../lib/types'
import { useLoad } from '../../lib/useLoad'
import { BarList, Card, CollectBar, DashFilter, PLAN_TYPE, dimRows, n, pct, useDashQuery, valueLabel } from './dash'

type SortKey = 'views' | 'clicks' | 'outbounds' | 'rate'

const rate = (p: DashPlan) => (p.views > 0 ? p.outbounds / p.views : -1)

/** DS-02 관심사: 고객이 찾는 조건 (계산기 · 본 요금제) · 요금제별 조회 → 클릭 → 개통하기 · 제휴사 · 클릭 위치 · 0건 조건 */
export default function DashInterest() {
  const { can } = useMe()
  const { q, setQ, params } = useDashQuery()
  const { data, error, setData } = useLoad(() => get<Data>('/dashboard/interest' + params), [params])
  const [sort, setSort] = useState<SortKey>('views')
  const [type, setType] = useState('')

  const partnerName = useMemo(() => {
    const names = new Map((data?.partners ?? []).map((p) => [p.partnerCode, p.partnerName]))
    return (code: string) => names.get(code) ?? code
  }, [data])

  const plans = useMemo(() => {
    const list = (data?.plans ?? []).filter((p) => !type || p.planType === type)
    const key = (p: DashPlan) => (sort === 'rate' ? rate(p) : p[sort])
    return [...list].sort((a, b) => key(b) - key(a))
  }, [data, sort, type])

  const dim = (d: string) => (data ? dimRows(data, d, partnerName) : [])
  const th = (k: SortKey, label: string) => (
    <th className="num sortable" aria-sort={sort === k ? 'descending' : 'none'} onClick={() => setSort(k)}>
      {label}
      {sort === k ? ' ▼' : ''}
    </th>
  )

  return (
    <>
      <PageHead title="대시보드 · 관심사" />
      <DashFilter q={q} onChange={setQ} csv={can('DS-02', 'DOWNLOAD') ? '/admin/api/dashboard/interest.csv' + params : undefined} />
      {!data ? (
        <Loading error={error} />
      ) : (
        <>
          <CollectBar settings={data.settings} onChange={(s) => setData({ ...data, settings: s })} />
          <div className="dash-grid">
            <Card title="요금 계산기 · 데이터 사용량" note="고객이 실제 쓰는 양">
              <BarList rows={dim('CALC_DATA')} />
            </Card>
            <Card title="요금 계산기 · 통신망">
              <BarList rows={dim('CALC_NETWORK')} />
            </Card>
            <Card title="본 요금제 · 데이터 제공량">
              <BarList rows={dim('VIEW_DATA')} />
            </Card>
            <Card title="본 요금제 · 요금대" note="후불 월 요금 · 선불 충전 금액">
              <BarList rows={dim('VIEW_PRICE')} />
            </Card>
            <Card title="본 요금제 · 통신망 · 세대 · 구분">
              <BarList
                rows={[
                  ...dim('VIEW_NETWORK'),
                  ...(data.dims.VIEW_GEN ?? []).map((i) => ({ label: i.value, value: i.cnt })),
                  ...dim('VIEW_TYPE'),
                ]}
                empty="아직 데이터가 없습니다"
              />
            </Card>
            <Card title="클릭한 위치" note="요금제 · 글 · 이동 링크">
              <BarList rows={dim('CLICK_POS')} />
            </Card>
            <Card title="제휴사별" note="조회 · 개통하기" wide>
              {data.partners.length === 0 ? (
                <div className="dash-empty">아직 데이터가 없습니다</div>
              ) : (
                <table className="grid">
                  <thead>
                    <tr>
                      <th>제휴사</th>
                      <th className="num">조회</th>
                      <th className="num">클릭</th>
                      <th className="num">개통하기</th>
                      <th className="num">개통 전환율</th>
                      <th className="num">제휴사 탭 선택</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.partners.map((p) => (
                      <tr key={p.partnerCode}>
                        <td>{p.partnerName}</td>
                        <td className="num">{n(p.views)}</td>
                        <td className="num">{n(p.clicks)}</td>
                        <td className="num">{n(p.outbounds)}</td>
                        <td className="num">{pct(p.outbounds, p.views)}</td>
                        <td className="num">{n(data.dims.PARTNER_TAB?.find((i) => i.value === p.partnerCode)?.cnt ?? 0)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </Card>
            <Card title="조건에 맞는 요금제가 없었던 계산" note="상품 기획 참고" wide>
              <BarList rows={dim('CALC_ZERO')} empty="없음 — 모든 계산에 결과가 있었습니다" />
            </Card>
            <Card title="요금제별 조회 → 클릭 → 개통하기" note="머리글을 누르면 정렬" wide>
              <div className="filter-row">
                <label>
                  구분{' '}
                  <select value={type} onChange={(e) => setType(e.target.value)}>
                    <option value="">전체</option>
                    <option value="POSTPAID">후불</option>
                    <option value="PREPAID">선불</option>
                  </select>
                </label>
                <span className="muted">{n(plans.length)}개 요금제</span>
              </div>
              {plans.length === 0 ? (
                <div className="dash-empty">아직 데이터가 없습니다</div>
              ) : (
                <div className="table-scroll">
                  <table className="grid">
                    <thead>
                      <tr>
                        <th>제휴사</th>
                        <th>요금제</th>
                        <th>구분</th>
                        <th>망 · 세대</th>
                        <th className="num">요금</th>
                        {th('views', '조회')}
                        {th('clicks', '클릭')}
                        {th('outbounds', '개통하기')}
                        {th('rate', '전환율')}
                      </tr>
                    </thead>
                    <tbody>
                      {plans.slice(0, 200).map((p) => (
                        <tr key={p.planId}>
                          <td>{p.partnerName ?? '–'}</td>
                          <td>
                            {p.name} <span className="muted">#{p.planId}</span>
                          </td>
                          <td>{p.planType ? (PLAN_TYPE[p.planType] ?? p.planType) : '–'}</td>
                          <td>
                            {p.network ? (NETWORK[p.network] ?? p.network) : '–'} · {p.generation ?? '–'}
                          </td>
                          <td className="num">{p.price != null ? won(p.price) : '–'}</td>
                          <td className="num">{n(p.views)}</td>
                          <td className="num">{n(p.clicks)}</td>
                          <td className="num">{n(p.outbounds)}</td>
                          <td className="num">{pct(p.outbounds, p.views)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              {plans.length > 200 && <p className="muted small">상위 200개만 보입니다. 전체는 엑셀 다운로드로 받아 주세요.</p>}
            </Card>
          </div>
          <p className="muted small">
            제휴사 탭 선택: {(data.dims.PARTNER_TAB ?? []).map((i) => `${valueLabel('PARTNER_TAB', i.value, partnerName)} ${n(i.cnt)}`).join(' · ') || '없음'}
          </p>
        </>
      )}
    </>
  )
}

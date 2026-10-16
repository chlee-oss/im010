import { useMemo, useState } from 'react'
import { api } from '../api/client'
import { pickMonthlyFallback } from '../lib/monthlyFallback'
import { BrandChip, usePartners } from '../lib/partners'
import { pickTwo } from '../lib/planPicks'
import { useApi } from '../lib/useApi'
import { PlanCard } from './PlanCard'
import { PlanListCard } from './PlanListCard'
import StateMessage from './StateMessage'

/**
 * 이달의 요금제 — 제휴사 "이달의 요금제" URL 수집 결과 (제휴사 이름순 → 사이트 순서, 배지 없음).
 * 수집 결과가 없으면 후불 QoS 5Mbps 요금제(5G 먼저, 모자라면 LTE)에서 제휴사를 돌아가며 무작위 6개를 대신 보여 준다.
 */
export function MonthlyPlans() {
  const monthly = useApi((signal) => api.monthlyPlans(signal), [])
  const empty = !!monthly.data && monthly.data.length === 0
  const all = useApi((signal) => (empty ? api.plans('POSTPAID', undefined, signal) : Promise.resolve(null)), [empty])
  // 대신 보여 줄 때는 고른 기준(망 · 5G · 소진 후 속도)을 태그로 보여 준다
  const fallback = useMemo(
    () =>
      (all.data ? pickMonthlyFallback(all.data) : []).map((p) =>
        p.tags.length > 0 ? p : { ...p, tags: [`${p.networkLabel}망`, p.generation, `소진 후 ${p.qos}`] },
      ),
    [all.data],
  )
  const data = empty ? (all.data ? fallback : null) : monthly.data
  const loading = monthly.loading || (empty && all.loading)
  const error = monthly.error ?? (empty ? all.error : null)
  const [month] = useState(() => new Date().getMonth() + 1)
  return (
    <section id="month">
      <div className="wrap">
        <div className="sec-head">
          <div>
            <h2>이달의 요금제비교</h2>
            <div className="sub">
              {empty
                ? `${fallback.length > 0 && fallback.every((p) => p.generation === '5G') ? '5G · ' : ''}데이터 소진 후 5Mbps 요금제를 제휴사별로 골라 봤어요`
                : `${month}월, 제휴사가 추천하는 이달의 요금제 모음`}
            </div>
          </div>
          <a className="see-all" href="#partners">
            전체 요금제 보기 →
          </a>
        </div>
        <StateMessage loading={loading && !data} error={error} empty={data?.length === 0} />
        <div className="month-scroll">{data?.map((p) => <PlanCard key={p.id} plan={p} />)}</div>
      </div>
    </section>
  )
}

/** 제휴사별 요금제 — 게시 중인 후불 요금제. [전체]는 제휴사마다 2개씩 한 줄, 제휴사 탭은 그 제휴사 전체 (낮은 가격순). */
export function PartnerPlans() {
  const partners = usePartners()
  const [tab, setTab] = useState<string>(() => {
    // 헤더 드롭다운에서 브랜드를 고르고 들어온 경우
    const picked = sessionStorage.getItem('im010.partnerTab')
    sessionStorage.removeItem('im010.partnerTab')
    return picked ?? 'all'
  })
  const { data, loading, error } = useApi((signal) => api.plans('POSTPAID', undefined, signal), [])
  const all = data ?? []
  const groups = [...partners.values()]
    .filter((pt) => tab === 'all' || pt.code === tab)
    .map((pt) => {
      const own = all.filter((p) => p.partnerCode === pt.code)
      return { partner: pt, total: own.length, plans: tab === 'all' ? pickTwo(own) : own }
    })
    .filter((g) => g.plans.length > 0)
  const tabName = tab === 'all' ? '전체' : (partners.get(tab)?.name ?? tab)
  const shown = groups.reduce((n, g) => n + g.plans.length, 0)
  const openPartner = (code: string) => {
    setTab(code)
    document.getElementById('partners')?.scrollIntoView({ behavior: 'smooth' })
  }

  return (
    <section id="partners" className="partner-band">
      <div className="wrap">
        <div className="sec-head">
          <div>
            <h2>제휴사별 요금제</h2>
            <div className="sub">{partners.size || 7}개 제휴 통신사 요금제를 브랜드별로 2개씩 모아봤어요 · 낮은 가격순</div>
          </div>
        </div>
        <div className="partner-tabs" role="tablist" aria-label="제휴사 선택">
          {[{ code: 'all', name: '전체' }, ...partners.values()].map((p) => (
            <button
              key={p.code}
              type="button"
              role="tab"
              className={tab === p.code ? 'active' : ''}
              aria-selected={tab === p.code}
              onClick={() => setTab(p.code)}
            >
              {p.name}
            </button>
          ))}
        </div>
        <p className="grid-count" aria-live="polite">
          {tab === 'all' ? (
            <>
              전체 · 제휴사별 대표 요금제 <b>{shown}</b>개
            </>
          ) : (
            <>
              {tabName} 요금제 <b>{shown}</b>개
            </>
          )}
        </p>
        <StateMessage loading={loading && !data} error={error} empty={!!data && shown === 0} />
        {groups.map((g) => (
          <div className="lp-group" key={g.partner.code}>
            {tab === 'all' && (
              <div className="lp-head">
                <BrandChip code={g.partner.code} name={g.partner.name} />
                {g.total > g.plans.length && (
                  <button type="button" className="lp-all" onClick={() => openPartner(g.partner.code)}>
                    {g.partner.name} 요금제 {g.total}개 전체 보기 ›
                  </button>
                )}
              </div>
            )}
            <div className="lp-pair">
              {g.plans.map((p) => (
                <PlanListCard key={p.id} plan={p} />
              ))}
            </div>
          </div>
        ))}
        <p className="data-basis">
          ※ 각 제휴사 공시 요금(VAT 포함). 개통 조건은 요금제 상세의 "개통하기"로 이동한 제휴사 페이지에서 확인해 주세요.
        </p>
      </div>
    </section>
  )
}

/** 선불요금제 — 카드만, 어떤 비교에도 넣지 않는다. */
export function PrepaidPlans() {
  const { data, loading, error } = useApi((signal) => api.plans('PREPAID', undefined, signal), [])
  return (
    <section id="prepaid">
      <div className="wrap">
        <div className="sec-head">
          <div>
            <h2>선불요금제</h2>
            <div className="sub">약정 없이 쓰는 선불 요금제 · 후불 요금제와 비교하지 않아요</div>
          </div>
        </div>
        <StateMessage loading={loading && !data} error={error} empty={data?.length === 0} />
        <div className="month-scroll">{data?.map((p) => <PlanCard key={p.id} plan={p} />)}</div>
      </div>
    </section>
  )
}

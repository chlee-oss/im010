import { useState } from 'react'
import { api } from '../api/client'
import { usePartners } from '../lib/partners'
import { useApi } from '../lib/useApi'
import { PlanCard, PlanTile } from './PlanCard'
import StateMessage from './StateMessage'

/** 이달의 요금제 — 제휴사 "이달의 요금제" URL 수집 결과 (제휴사 이름순 → 사이트 순서, 배지 없음). */
export function MonthlyPlans() {
  const { data, loading, error } = useApi((signal) => api.monthlyPlans(signal), [])
  const [month] = useState(() => new Date().getMonth() + 1)
  return (
    <section id="month">
      <div className="wrap">
        <div className="sec-head">
          <div>
            <h2>이달의 요금제비교</h2>
            <div className="sub">{month}월, 제휴사가 추천하는 이달의 요금제 모음</div>
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

/** 제휴사별 요금제 — 게시 중인 후불 요금제, 브랜드 탭 필터, 낮은 가격순. */
export function PartnerPlans() {
  const partners = usePartners()
  const [tab, setTab] = useState<string>(() => {
    // 헤더 드롭다운에서 브랜드를 고르고 들어온 경우
    const picked = sessionStorage.getItem('im010.partnerTab')
    sessionStorage.removeItem('im010.partnerTab')
    return picked ?? 'all'
  })
  const { data, loading, error } = useApi((signal) => api.plans('POSTPAID', undefined, signal), [])
  const list = (data ?? []).filter((p) => tab === 'all' || p.partnerCode === tab)
  const tabName = tab === 'all' ? '전체' : (partners.get(tab)?.name ?? tab)

  return (
    <section id="partners" className="partner-band">
      <div className="wrap">
        <div className="sec-head">
          <div>
            <h2>제휴사별 요금제</h2>
            <div className="sub">{partners.size || 7}개 제휴 통신사 요금제를 브랜드별로 모아봤어요 · 낮은 가격순</div>
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
          {tabName} 요금제 <b>{list.length}</b>개
        </p>
        <StateMessage loading={loading && !data} error={error} empty={!!data && list.length === 0} />
        <div className="partner-grid">
          {list.map((p) => (
            <PlanTile key={p.id} plan={p} />
          ))}
        </div>
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

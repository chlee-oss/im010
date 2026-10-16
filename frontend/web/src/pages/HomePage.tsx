import { useEffect } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { api } from '../api/client'
import Calculator from '../components/Calculator'
import { Analysis, TrustRow } from '../components/Insights'
import { InternetBanner, InternetProducts } from '../components/Internet'
import { MonthlyPlans, PartnerPlans, PrepaidPlans } from '../components/PlanSections'
import { won } from '../lib/format'
import { BrandChip } from '../lib/partners'
import { heroPicks } from '../lib/planRules'
import { useApi } from '../lib/useApi'
import { usePageTitle } from '../lib/usePageTitle'
import { useToast } from '../lib/toast'

export default function HomePage() {
  usePageTitle()
  const location = useLocation()
  const toast = useToast()

  useEffect(() => {
    // /go/internet 이 신청 URL이 없어 되돌려 보낸 경우
    if (new URLSearchParams(location.search).get('forward') === 'unavailable') {
      toast('신청 페이지를 준비 중이에요. 잠시 후 다시 시도해 주세요')
    }
    // 다른 페이지에서 /#section 으로 들어온 경우 해당 섹션으로 이동
    if (location.hash) {
      requestAnimationFrame(() => document.getElementById(location.hash.slice(1))?.scrollIntoView())
    }
  }, [location.search, location.hash, toast])

  return (
    <>
      <section className="hero">
        <div className="wrap">
          <div>
            <span className="eyebrow">● 7개 제휴사 요금제 한눈에 비교</span>
            <h1 className="hero-title">
              매달 통신비,
              <br />
              <em>얼마나</em> 아낄 수 있을까요?
            </h1>
            <p className="hero-sub">
              데이터 사용량과 원하는 통신망만 알려주세요.
              <br />
              제휴사 요금제 중 조건에 맞는 최저가 요금제를 바로 찾아 드려요.
            </p>
            <Calculator />
          </div>
          <HeroCards />
        </div>
      </section>
      <MonthlyPlans />
      <PartnerPlans />
      <PrepaidPlans />
      <InternetBanner />
      <InternetProducts />
      <Analysis />
      <TrustRow />
    </>
  )
}

// Hero 플로팅 카드 — 게시 중인 후불 요금제 중 QoS 5Mbps 요금제를 월 요금이 낮은 순으로 3개 (같은 요금제는 하나만, lib/planRules). 없으면 비워 둔다
function HeroCards() {
  const { data } = useApi((signal) => api.plans('POSTPAID', undefined, signal), [])
  const plans = heroPicks(data ?? [])
  return (
    <div className="hero-visual">
      {plans.map((p, i) => (
        <Link key={p.id} className={`float-card fc-${i + 1}`} to={`/plans/${p.id}`}>
          <BrandChip code={p.partnerCode} name={p.partnerName} />
          <h4>{p.name}</h4>
          <div className="price-line num">
            {won(p.monthlyPrice)}
            <span>원 / 월</span>
          </div>
          <div className="meta">
            {p.networkLabel} 망 · {p.generation} · QoS {p.qos}
          </div>
        </Link>
      ))}
    </div>
  )
}

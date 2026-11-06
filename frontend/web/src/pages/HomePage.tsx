import { useEffect, useMemo } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { api } from '../api/client'
import Calculator from '../components/Calculator'
import { Analysis } from '../components/Insights'
import { InternetBanner, InternetProducts } from '../components/Internet'
import { MonthlyPlans, PartnerPlans, PrepaidPlans } from '../components/PlanSections'
import { won } from '../lib/format'
import { BrandChip } from '../lib/partners'
import { heroPicks } from '../lib/planRules'
import { useApi } from '../lib/useApi'
import { usePageTitle } from '../lib/usePageTitle'
import { useToast } from '../lib/toast'
import Icon from '../components/Icon'

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
            <span className="eyebrow"><Icon name="compare_arrows" /> 7개 제휴사 요금제 한눈에 비교</span>
            <h1 className="hero-title">
              매달 통신비,
              <br />
              <em>얼마나</em> 아낄 수 있을까요?
            </h1>
            <p className="hero-sub">
              데이터 사용량과 원하는 통신망만 알려주세요.{' '}
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
    </>
  )
}

// Hero 플로팅 카드 — 게시 중인 선불 요금제 4개를 제휴사마다 고르게 무작위로 (lib/planRules heroPicks). 화면을 열 때마다 바뀌고, 없으면 비워 둔다
function HeroCards() {
  const { data } = useApi((signal) => api.plans('PREPAID', undefined, signal), [])
  const plans = useMemo(() => heroPicks(data ?? []), [data])
  return (
    <div className="hero-visual">
      {plans.map((p, i) => (
        <Link key={p.id} className={`float-card fc-${i + 1}`} to={`/plans/${p.id}`}>
          <BrandChip code={p.partnerCode} name={p.partnerName} />
          <h4>{p.name}</h4>
          <div className="price-line num">
            {won(p.chargePrice)}
            <span>{p.validDays ? `원 / ${p.validDays}일` : '원'}</span>
          </div>
          <div className="meta">
            {p.networkLabel} 망 · {p.generation}
            {p.qos ? ` · QoS ${p.qos}` : ''}
          </div>
        </Link>
      ))}
    </div>
  )
}

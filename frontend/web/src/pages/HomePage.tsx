import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import Calculator from '../components/Calculator'
import { Analysis, TrustRow } from '../components/Insights'
import { InternetBanner, InternetProducts } from '../components/Internet'
import { MonthlyPlans, PartnerPlans, PrepaidPlans } from '../components/PlanSections'
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
              데이터 사용량과 원하는 통신망만 알려주세요. 마블링·토리모바일·위너스텔·슈가모바일·인스모바일·조이M·시월모바일
              <br />
              요금제 중 조건에 맞는 최저가 요금제를 바로 찾아 드려요.
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

// Hero 플로팅 카드 — 프로모션관리 보류 중 임시 규칙은 "이달의 요금제 앞 3개"이지만, 1차 화면은 목업 장식 그대로 둔다.
function HeroCards() {
  return (
    <div className="hero-visual" aria-hidden="true">
      <div className="float-card fc-1">
        <span className="brand-chip mv">마블링</span>
        <h4>넉넉 11GB+ 요금제</h4>
        <div className="price-line num">
          16,500<span>원 / 월</span>
        </div>
        <div className="meta">SKT 망 · 5G · QoS 1Mbps</div>
      </div>
      <div className="float-card fc-2">
        <span className="brand-chip im">인스모바일</span>
        <h4>가벼운 3GB 요금제</h4>
        <div className="price-line num">
          8,900<span>원 / 월</span>
        </div>
        <div className="meta">LGU+ 망 · LTE</div>
      </div>
      <div className="float-card fc-3">
        <span className="brand-chip jt">조이M</span>
        <h4>무제한 요금제</h4>
        <div className="price-line num">
          24,900<span>원 / 월</span>
        </div>
        <div className="meta">KT 망 · 5G 완전 무제한</div>
      </div>
    </div>
  )
}

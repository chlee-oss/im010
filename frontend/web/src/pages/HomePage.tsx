import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import Calculator from '../components/Calculator'
import { Analysis } from '../components/Insights'
import { InternetBanner, InternetProducts } from '../components/Internet'
import { PartnerPlans, PrepaidPlans } from '../components/PlanSections'
import { WeeklyTabs } from '../components/WeeklyTabs'
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
            <span className="eyebrow"><Icon name="compare_arrows" /> 조건에 맞는 제휴사 요금제를 찾아드려요</span>
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
          </div>
          {/* 오른쪽: 계산기 (예전 선불 요금제 그림 카드 자리 — 선불은 금주의 요금 [선불은어때] 탭에서) */}
          <Calculator />
        </div>
      </section>
      <WeeklyTabs />
      <PartnerPlans />
      <PrepaidPlans />
      <InternetBanner />
      <InternetProducts />
      <Analysis />
    </>
  )
}

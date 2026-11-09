import { useEffect } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { InternetBanner, InternetProducts } from '../components/Internet'
import { MonthlyPlans, PartnerPlans, PrepaidPlans } from '../components/PlanSections'
import { usePageTitle } from '../lib/usePageTitle'

/**
 * 서비스별 랜딩 (/postpaid · /prepaid · /internet) — 검색 결과에 서비스마다 따로 보이도록 메인 섹션을 독립 주소로 보여 준다.
 * 제목은 "im010 - …" 그대로 (| im010 을 붙이지 않음). 미리 렌더링(scripts/prerender.mjs)의 SERVICES 와 문구를 맞춘다.
 */
export type ServiceKey = 'postpaid' | 'prepaid' | 'internet'

const SERVICE_PAGES: Record<ServiceKey, { path: string; title: string; h1: string; lead: string }> = {
  postpaid: {
    path: '/postpaid',
    title: 'im010 - 나는 알뜰한 요금제',
    h1: '나는 알뜰한 요금제',
    lead: '제휴사 후불 알뜰폰 요금제를 월 요금 · 데이터 · 통신망별로 비교해 보세요.',
  },
  prepaid: {
    path: '/prepaid',
    title: 'im010 - 나는 선불요금제',
    h1: '나는 선불요금제',
    lead: '제휴사 선불 알뜰폰 요금제를 충전 금액 · 사용 기간 · 데이터별로 비교해 보세요.',
  },
  internet: {
    path: '/internet',
    title: 'im010 - 나는 알뜰한 인터넷',
    h1: '나는 알뜰한 인터넷',
    lead: '인터넷 단독 · 인터넷 + TV 결합 상품의 월 요금과 혜택을 통신사별로 비교해 보세요.',
  },
}

export default function ServiceLandingPage({ service }: { service: ServiceKey }) {
  const page = SERVICE_PAGES[service]
  usePageTitle(page.title, { raw: true })
  const { hash } = useLocation()

  // /postpaid#partners 처럼 섹션 주소로 들어오면 그 섹션으로 (메인과 같은 방식)
  useEffect(() => {
    if (hash) requestAnimationFrame(() => document.getElementById(hash.slice(1))?.scrollIntoView())
  }, [hash])

  return (
    <>
      <section className="service-hero">
        <div className="wrap">
          <nav className="breadcrumb" aria-label="현재 위치">
            <Link to="/">홈</Link> › {page.h1}
          </nav>
          <h1>{page.h1}</h1>
          <p>{page.lead}</p>
          <nav className="service-tabs" aria-label="서비스">
            {(Object.keys(SERVICE_PAGES) as ServiceKey[]).map((k) => (
              <Link key={k} to={SERVICE_PAGES[k].path} className={k === service ? 'on' : ''} aria-current={k === service ? 'page' : undefined}>
                {SERVICE_PAGES[k].h1}
              </Link>
            ))}
            <Link to="/ai">나는 알뜰한 통신경험을 원해!</Link>
          </nav>
        </div>
      </section>
      {service === 'postpaid' && (
        <>
          <MonthlyPlans />
          <PartnerPlans />
        </>
      )}
      {service === 'prepaid' && <PrepaidPlans />}
      {service === 'internet' && (
        <>
          <InternetBanner />
          <InternetProducts />
        </>
      )}
    </>
  )
}

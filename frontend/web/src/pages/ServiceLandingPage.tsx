import { Link } from 'react-router-dom'
import Calculator from '../components/Calculator'
import { FindPresets } from '../components/FindPresets'
import { InternetBanner, InternetProducts } from '../components/Internet'
import { PartnerPlans, PrepaidPlans } from '../components/PlanSections'
import { WeeklyTabs } from '../components/WeeklyTabs'
import { useHashScroll } from '../lib/scrollHold'
import { usePageTitle } from '../lib/usePageTitle'

/**
 * 서비스별 랜딩 — 메인(/)의 섹션을 검색 결과에 따로 보이도록 독립 주소로 보여 준다.
 *   /weekly   im010 금주의 요금, 이렇게 추천해요   (메인 #month)
 *   /find     내가 찾는 요금제를 직접 찾아요       (메인 #calc-section + 조건별 바로가기)
 *   /postpaid 입점한 제휴사에서 소개해요           (메인 #partners)
 *   /prepaid  선불요금제를 알려 드릴게요           (메인 #prepaid)
 *   /internet 인터넷도 im010에서 한 번에           (메인 #internet + #internet-compare "인터넷 상품 비교를 해 보세요")
 * 제목은 "im010 - …" 그대로 (| im010 을 붙이지 않음). 백오피스 검색엔진설정(ST-08) 페이지별 제목이 있으면 그것을 쓴다.
 * 미리 렌더링(scripts/prerender.mjs)의 SERVICES · 백엔드 PageMeta.PAGES 와 문구를 맞춘다.
 */
export type ServiceKey = 'weekly' | 'find' | 'postpaid' | 'prepaid' | 'internet'

const SERVICE_PAGES: Record<ServiceKey, { path: string; title: string; h1: string; lead: string }> = {
  weekly: {
    path: '/weekly',
    title: 'im010 - 금주의 요금, 이렇게 추천해요',
    h1: 'im010 금주의 요금, 이렇게 추천해요',
    lead: '영상 · 데이터 · 통화 · 가격 기준별로 이번 주 제휴사 요금제를 하나씩 골라 추천해요.',
  },
  find: {
    path: '/find',
    title: 'im010 - 내가 찾는 요금제를 직접 찾아요',
    h1: '내가 찾는 요금제를 직접 찾아요',
    lead: '데이터 사용량과 통신망을 고르거나, 조건별로 모아 둔 요금제에서 바로 찾아보세요.',
  },
  postpaid: {
    path: '/postpaid',
    title: 'im010 - 입점한 제휴사에서 소개해요',
    h1: '입점한 제휴사에서 소개해요',
    lead: '제휴사별 후불 알뜰폰 요금제를 월 요금 · 데이터 · 통신망별로 비교해 보세요.',
  },
  prepaid: {
    path: '/prepaid',
    title: 'im010 - 선불요금제를 알려 드릴게요',
    h1: '선불요금제를 알려 드릴게요',
    lead: '약정 없이 쓰는 선불 알뜰폰 요금제를 충전 금액 · 사용 기간 · 데이터별로 비교해 보세요.',
  },
  internet: {
    path: '/internet',
    title: 'im010 - 인터넷도 im010에서 한 번에',
    h1: '인터넷도 im010에서 한 번에',
    lead: '인터넷 단독 · 인터넷 + TV 결합 상품의 월 요금과 혜택을 통신사별로 비교해 보세요.',
  },
}

export default function ServiceLandingPage({ service }: { service: ServiceKey }) {
  const page = SERVICE_PAGES[service]
  usePageTitle(page.title, { raw: true, page: service })

  // /postpaid#partners 처럼 섹션 주소로 들어오면 그 섹션으로 (메인과 같은 방식)
  useHashScroll()

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
      {service === 'weekly' && <WeeklyTabs heading="이번 주 추천 기준" />}
      {service === 'find' && (
        <>
          <section id="calc-section" aria-label="조건으로 최저가 찾기">
            <div className="wrap">
              <div className="calc-band">
                <div className="calc-copy">
                  <h2>조건으로 최저가 찾기</h2>
                  <p>데이터 사용량과 원하는 통신망만 알려 주세요. 제휴사 요금제 중 조건에 맞는 최저가를 바로 찾아 드려요.</p>
                  <Calculator />
                </div>
              </div>
            </div>
          </section>
          <FindPresets />
        </>
      )}
      {service === 'postpaid' && <PartnerPlans />}
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

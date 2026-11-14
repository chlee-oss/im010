import { useEffect } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { api } from '../api/client'
import { AiCard } from '../components/AiCard'
import Calculator from '../components/Calculator'
import { Analysis } from '../components/Insights'
import { InternetBanner, InternetProducts } from '../components/Internet'
import { PartnerPlans, PrepaidPlans } from '../components/PlanSections'
import { WeeklyTabs } from '../components/WeeklyTabs'
import { usePartners } from '../lib/partners'
import { useApi } from '../lib/useApi'
import { usePageTitle } from '../lib/usePageTitle'
import { useToast } from '../lib/toast'
import Icon from '../components/Icon'

/**
 * 메인 (시안 B-1): 서비스 소개 → im010 금주의 요금을 추천해요 → 내 조건으로 직접 찾기 →
 * 제휴사별 요금제 → 선불요금제 → 인터넷 → 요금제분석 → 알뜰AI
 */
export default function HomePage() {
  usePageTitle()
  const location = useLocation()
  const toast = useToast()
  const partners = [...usePartners().values()]

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
      <section className="home-intro">
        <div className="wrap">
          <h1>
            합리적인 알뜰한 통신생활!
            <span className="line2">
              알뜰폰과 인터넷은 <b>im010</b>이 추천해 드릴게요.
            </span>
          </h1>
          {partners.length > 0 && (
            <p className="intro-partners">
              <span className="intro-avatars" aria-hidden="true">
                {partners.map((p) => (
                  <span key={p.code} style={{ background: p.chipBg, color: p.chipFg }}>
                    {p.name.slice(0, 1)}
                  </span>
                ))}
              </span>
              제휴사 {partners.length}곳 요금제를 같은 기준으로 비교해요
            </p>
          )}
          {/* 섹션별 독립 주소 — 검색엔진이 메뉴 구조를 알 수 있게 일반 링크로 (서비스 랜딩) */}
          <nav className="home-jump" aria-label="바로가기">
            <Link to="/weekly">금주의 요금, 이렇게 추천해요</Link>
            <Link to="/find">내가 찾는 요금제를 직접 찾아요</Link>
            <Link to="/postpaid">입점한 제휴사에서 소개해요</Link>
            <Link to="/prepaid">선불요금제를 알려 드릴게요</Link>
            <Link to="/internet">인터넷도 im010에서 한 번에</Link>
          </nav>
        </div>
      </section>

      <WeeklyTabs />

      <section id="calc-section" aria-label="내가 찾는 요금제를 직접 찾아요">
        <div className="wrap">
          <div className="calc-band">
            <div className="calc-copy">
              <h2>내가 찾는 요금제를 직접 찾아요</h2>
              <p>데이터 사용량과 원하는 통신망만 알려 주세요. 제휴사 요금제 중 조건에 맞는 최저가를 바로 찾아 드려요.</p>
              <Calculator />
            </div>
            <FamilyArt />
          </div>
        </div>
      </section>

      <PartnerPlans />
      <PrepaidPlans />
      <InternetBanner />
      <InternetProducts />
      <Analysis />
      <AiLatest />
    </>
  )
}

/** 가족 일러스트: 서로 겹친 두 원 위의 세 사람 (시안 B "함께 · 상생"). 장식 */
function FamilyArt() {
  return (
    <div className="family-art" aria-hidden="true">
      <svg viewBox="0 0 360 230">
        <circle cx="130" cy="115" r="92" fill="#E6EFFF" />
        <circle cx="230" cy="115" r="92" fill="#D6E6FF" fillOpacity="0.85" />
        <circle cx="104" cy="97" r="22" fill="var(--primary)" />
        <rect x="80" y="125" width="48" height="56" rx="24" fill="var(--primary)" />
        <circle cx="180" cy="109" r="16" fill="#4D8DF7" />
        <rect x="162" y="131" width="36" height="44" rx="18" fill="#4D8DF7" />
        <circle cx="254" cy="97" r="22" fill="#7FB0FF" />
        <rect x="230" y="125" width="48" height="56" rx="24" fill="#7FB0FF" />
      </svg>
    </div>
  )
}

/** 알뜰AI 최신 글 4개 (글이 없으면 영역을 숨긴다) */
function AiLatest() {
  const { data } = useApi((signal) => api.aiPosts({ page: 1 }, signal), [])
  const cards = data ? [...(data.pinned ? [data.pinned] : []), ...data.items].slice(0, 4) : []
  if (cards.length === 0) return null
  return (
    <section id="ai-latest" aria-label="알뜰AI">
      <div className="wrap">
        <div className="sec-head">
          <div>
            <h2>알뜰AI</h2>
            <div className="sub">알뜰통신 · 인터넷 · 잡식잡식, 통신비 아끼는 이야기</div>
          </div>
          <Link className="see-all" to="/ai">
            알뜰AI 더 보기 <Icon name="arrow_forward" />
          </Link>
        </div>
        <div className="ai-grid">
          {cards.map((c) => (
            <AiCard key={c.id} card={c} />
          ))}
        </div>
      </div>
    </section>
  )
}

import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import type { PlanSummary } from '../api/types'
import { won } from '../lib/format'
import { BrandChip } from '../lib/partners'
import { track } from '../lib/track'
import { useApi } from '../lib/useApi'
import { headline, pickWeeklyTabs } from '../lib/weeklyTabs'
import Icon from './Icon'
import StateMessage from './StateMessage'

/** 탭 아이콘 (시안 B-1: 이모지 대신 선 아이콘) */
const WEEKLY_ICON: Record<string, string> = {
  video: 'smart_display',
  long: 'schedule',
  call: 'call',
  save: 'savings',
  prepaid: 'credit_card',
}

/**
 * im010 금주의 요금을 추천해요 — 탭(영상많이 · 오래오래 · 통화많이 · 알뜰하게 · 선불은어때)마다 규칙으로 제휴사별 1개 (lib/weeklyTabs).
 * 요금제가 없는 탭은 숨긴다. 제휴사 순서는 화면을 열 때마다 랜덤.
 * 카드는 가운데를 강조하는 캐러셀: 화살표 · 점 · 옆 카드 누르기 · 손가락 밀기 · 6초 자동 넘김(마우스를 올리면 멈춤).
 * 섹션 주소는 예전 이달의 요금제와 같은 #month (머리말 메뉴 · 바깥 링크 · 클릭 위치 통계 MONTHLY 유지).
 */
export function WeeklyTabs() {
  const postpaid = useApi((signal) => api.plans('POSTPAID', undefined, signal), [])
  const prepaid = useApi((signal) => api.plans('PREPAID', undefined, signal), [])
  const tabs = useMemo(
    () => (postpaid.data && prepaid.data ? pickWeeklyTabs([...postpaid.data, ...prepaid.data]) : null),
    [postpaid.data, prepaid.data],
  )
  const [tabKey, setTabKey] = useState<string | null>(null)
  const tab = tabs?.find((t) => t.key === tabKey) ?? tabs?.[0]

  return (
    <section id="month" className="weekly">
      <div className="wrap">
        <div className="weekly-head">
          <h2>
            <span className="brand">im010</span> 금주의 요금을 추천해요
          </h2>
        </div>
        <StateMessage
          loading={!tabs && (postpaid.loading || prepaid.loading)}
          error={postpaid.error ?? prepaid.error}
          empty={tabs?.length === 0}
        />
        {tabs && tab && (
          <>
            <div className="weekly-tabs" role="tablist" aria-label="추천 기준">
              {tabs.map((t) => (
                <button
                  key={t.key}
                  type="button"
                  role="tab"
                  aria-selected={t.key === tab.key}
                  className={t.key === tab.key ? 'on' : ''}
                  onClick={() => {
                    setTabKey(t.key)
                    track('filter', { props: { dim: 'weekly_tab', value: t.key } })
                  }}
                >
                  <span className="ico" aria-hidden="true">
                    <Icon name={WEEKLY_ICON[t.key] ?? 'smartphone'} />
                  </span>
                  {t.label}
                </button>
              ))}
            </div>
            <p className="weekly-desc">{tab.desc}</p>
            {/* 탭이 바뀌면 캐러셀을 처음 자리부터 */}
            <Carousel key={tab.key} plans={tab.plans} />
          </>
        )}
        <p className="weekly-foot">
          요금은 제휴사 공시 기준이며 개통 페이지에서 최종 확인해 주세요.{' '}
          <a href="#partners">
            전체 요금제 보기 <Icon name="arrow_forward" />
          </a>
        </p>
      </div>
    </section>
  )
}

function Carousel({ plans }: { plans: PlanSummary[] }) {
  const n = plans.length
  // 처음에는 두 번째 카드를 가운데 (양옆이 다 차게). step = 처음 자리에서 몇 칸 넘겼는지
  const start = n >= 3 ? 1 : 0
  const [step, setStep] = useState(0)
  const cur = (((start + step) % n) + n) % n
  const [paused, setPaused] = useState(false)
  const touchX = useRef<number | null>(null)
  const go = (i: number) => setStep(i - start)

  useEffect(() => {
    if (n < 2 || paused) return
    const t = setInterval(() => setStep((s) => s + 1), 6000)
    return () => clearInterval(t)
  }, [n, paused])

  /** 가운데에서 몇 칸 떨어졌는지 (-2 ~ 2, 고리 모양) */
  const offset = (i: number) => {
    let d = i - cur
    if (d > n / 2) d -= n
    if (d < -n / 2) d += n
    return Math.max(-2, Math.min(2, d))
  }

  return (
    <>
      <div
        className="weekly-carousel"
        aria-roledescription="carousel"
        onMouseEnter={() => setPaused(true)}
        onMouseLeave={() => setPaused(false)}
        onTouchStart={(e) => {
          touchX.current = e.touches[0].clientX
          setPaused(true)
        }}
        onTouchEnd={(e) => {
          const x0 = touchX.current
          touchX.current = null
          if (x0 == null) return
          const dx = e.changedTouches[0].clientX - x0
          if (Math.abs(dx) > 40) go(cur + (dx < 0 ? 1 : -1))
        }}
      >
        {n > 1 && (
          <button type="button" className="weekly-arrow prev" aria-label="이전 요금제" onClick={() => go(cur - 1)}>
            <Icon name="chevron_left" />
          </button>
        )}
        {plans.map((p, i) => (
          <WeeklyCard key={p.id} plan={p} pos={offset(i)} onFocusCard={() => go(i)} />
        ))}
        {n > 1 && (
          <button type="button" className="weekly-arrow next" aria-label="다음 요금제" onClick={() => go(cur + 1)}>
            <Icon name="chevron_right" />
          </button>
        )}
      </div>
      {n > 1 && (
        <div className="weekly-dots">
          {plans.map((p, i) => (
            <button key={p.id} type="button" className={i === cur ? 'on' : ''} aria-label={`${i + 1}번째 요금제`} onClick={() => go(i)} />
          ))}
        </div>
      )}
    </>
  )
}

/** 추천 카드: 가운데(pos 0)는 강조, 옆 카드를 누르면 가운데로 옮기고 가운데 카드를 누르면 S2 상세 */
function WeeklyCard({ plan: p, pos, onFocusCard }: { plan: PlanSummary; pos: number; onFocusCard: () => void }) {
  const prepaid = p.planType === 'PREPAID'
  const discount = !prepaid && p.discountMonths != null && p.priceAfterDiscount != null
  const hidden = Math.abs(pos) > 1
  return (
    <Link
      to={`/plans/${p.id}`}
      className={`weekly-card pos${pos}`}
      aria-hidden={hidden}
      tabIndex={hidden ? -1 : undefined}
      onClick={(e) => {
        if (pos !== 0) {
          e.preventDefault()
          onFocusCard()
        }
      }}
    >
      <span className="wk-partner">
        <BrandChip code={p.partnerCode} name={p.partnerName} />
      </span>
      <div className="wk-name">{p.name}</div>
      <div className="wk-data num">{headline(p)}</div>
      <div className="wk-spec">
        통화 {p.voice ?? '-'}
        <br />
        문자 {p.sms ?? '-'}
      </div>
      <div className="wk-net">
        {p.networkLabel}망 · {p.generation}
        {p.qos ? ` · 소진 후 ${p.qos}` : ''}
      </div>
      <div className="wk-price">
        {prepaid ? (
          <>
            <small>충전</small>
            <b className="num">{won(p.chargePrice)}</b>
            <small>원{p.validDays ? ` / ${p.validDays}일` : ''}</small>
          </>
        ) : (
          <>
            <small>월</small>
            <b className="num">{won(p.monthlyPrice)}</b>
            <small>원</small>
          </>
        )}
      </div>
      <div className="wk-after">{discount ? `${p.discountMonths}개월 이후 ${won(p.priceAfterDiscount)}원` : ' '}</div>
    </Link>
  )
}

import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { cheapestPlan, gbPriceByPartner, topDataBucket } from '../lib/analysis'
import { dotDate, won } from '../lib/format'
import { useApi } from '../lib/useApi'
import StateMessage from './StateMessage'

/** 요금제분석 — 오늘 게시 중인 후불 요금제만으로 계산한 값 (lib/analysis). 선불 요금제는 비교하지 않는다. */
export function Analysis() {
  const { data, loading, error } = useApi((signal) => api.plans('POSTPAID', undefined, signal), [])
  const [today] = useState(() => dotDate(new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Seoul' })))
  const plans = useMemo(() => data ?? [], [data])
  const gbPrice = useMemo(() => gbPriceByPartner(plans), [plans])
  const bucket = useMemo(() => topDataBucket(plans), [plans])
  const cheapest = useMemo(() => cheapestPlan(plans), [plans])
  const partners = useMemo(() => new Set(plans.map((p) => p.partnerCode)).size, [plans])
  const max = Math.max(1, ...gbPrice.map((g) => g.value))

  return (
    <section id="analysis" className="partner-band">
      <div className="wrap">
        <div className="sec-head">
          <div>
            <h2>요금제분석</h2>
            <div className="sub">게시 중인 후불 요금제로 비교했어요. 어떤 브랜드가 가장 합리적일까요? (선불 요금제는 비교하지 않아요)</div>
          </div>
        </div>
        <StateMessage loading={loading && !data} error={error} empty={!!data && plans.length === 0} />
        {plans.length > 0 && (
          <div className="analysis-grid">
            <div className="chart-card">
              <h4>후불 요금제 GB당 평균 월 요금 ({gbPrice.length}개 제휴사)</h4>
              {gbPrice.map((g, i) => (
                <div className="bar-row" key={g.partner}>
                  <span className="bar-label">{g.partner}</span>
                  <div className="bar-track">
                    <div className={`bar-fill${i === 0 ? ' best' : ''}`} style={{ width: `${((g.value / max) * 100).toFixed(1)}%` }} />
                  </div>
                  <span className="bar-val num">{won(g.value)}원</span>
                </div>
              ))}
              <p className="chart-note">
                ※ {today} 기준 게시 중인 후불 요금제 중 기본 데이터 1GB 이상 요금제의 1GB 환산 월 요금 평균. 무제한 · 매일 데이터 요금제는
                빼고, 할인 기간이 있는 요금제는 할인 중 요금으로 계산했어요. 낮을수록 데이터 효율이 좋습니다.
              </p>
            </div>
            <div className="stat-stack">
              <div className="stat-card">
                <div className="stat-lbl">게시 중인 후불 요금제</div>
                <div className="stat-val num">
                  {plans.length}개 <span className="stat-sub">· {partners}개 제휴사</span>
                </div>
              </div>
              {bucket && (
                <div className="stat-card">
                  <div className="stat-lbl">후불 요금제가 가장 많은 데이터 구간 (한 달 기준)</div>
                  <div className="stat-val num">
                    {bucket.label} <span className="stat-sub">· {bucket.count}개</span>
                  </div>
                </div>
              )}
              {cheapest && (
                <Link className="stat-card" to={`/plans/${cheapest.id}`}>
                  <div className="stat-lbl">후불 요금제 중 가장 낮은 월 요금</div>
                  <div className="stat-val num">월 {won(cheapest.monthlyPrice)}원</div>
                  <div className="stat-note">
                    {cheapest.partnerName} · {cheapest.name}
                  </div>
                </Link>
              )}
            </div>
          </div>
        )}
      </div>
    </section>
  )
}

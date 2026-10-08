import { api } from '../api/client'
import { useApi } from '../lib/useApi'

// 요금제분석 수치는 배치 집계(월 1회)가 생기기 전까지 예시 값. (백오피스 · 배치 단계에서 API로 교체)
const GB_PRICE = [
  { name: '위너스텔', value: 980 },
  { name: '조이M', value: 1050 },
  { name: '시월모바일', value: 1120 },
  { name: '토리모바일', value: 1180 },
  { name: '슈가모바일', value: 1260 },
  { name: '마블링', value: 1320 },
  { name: '인스모바일', value: 1450 },
]

export function Analysis() {
  const max = Math.max(...GB_PRICE.map((g) => g.value))
  return (
    <section id="analysis" className="partner-band">
      <div className="wrap">
        <div className="sec-head">
          <div>
            <h2>요금제분석</h2>
            <div className="sub">데이터로 보는 알뜰폰 요금제, 어떤 브랜드가 가장 합리적일까요?</div>
          </div>
        </div>
        <div className="analysis-grid">
          <div className="chart-card">
            <h4>GB당 평균 요금 비교 ({GB_PRICE.length}개 제휴사)</h4>
            {GB_PRICE.map((g, i) => (
              <div className="bar-row" key={g.name}>
                <span className="bar-label">{g.name}</span>
                <div className="bar-track">
                  <div className={`bar-fill${i === 0 ? ' best' : ''}`} style={{ width: `${((g.value / max) * 100).toFixed(1)}%` }} />
                </div>
                <span className="bar-val num">{g.value.toLocaleString('ko-KR')}원</span>
              </div>
            ))}
            <p className="chart-note">
              ※ 게시 중인 후불 요금제(무제한 제외)의 1GB 환산 평균 요금(예시 데이터). 낮을수록 데이터 효율이 좋습니다.
            </p>
          </div>
          <div className="stat-stack">
            <div className="stat-card">
              <div className="stat-lbl">이번 달 가장 많이 비교된 데이터 구간</div>
              <div className="stat-val num">11GB ~ 30GB</div>
            </div>
            <div className="stat-card">
              <div className="stat-lbl">이번 달 새로 게시된 요금제</div>
              <div className="stat-val num">9개</div>
            </div>
            <div className="stat-card">
              <div className="stat-lbl">가장 빠르게 성장 중인 요금제 구간</div>
              <div className="stat-val num">무제한형 요금제</div>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}

/** 신뢰 지표 — "비교 가능한 요금제"만 실데이터(게시 중 후불 요금제 수), 나머지는 예시 값. */
export function TrustRow() {
  const { data } = useApi((signal) => api.statsSummary(signal), [])
  return (
    <section>
      <div className="wrap">
        <div className="trust-row">
          <div>
            <div className="num">128,940</div>
            <div className="lbl">누적 개통 건수</div>
          </div>
          <div>
            <div className="num">4.8</div>
            <div className="lbl">평균 이용 만족도</div>
          </div>
          <div>
            <div className="num">7개사</div>
            <div className="lbl">제휴 통신사</div>
          </div>
          <div>
            <div className="num">{data ? data.comparablePlanCount : '—'}개</div>
            <div className="lbl">비교 가능한 요금제</div>
          </div>
        </div>
        <p className="trust-note">※ 비교 가능한 요금제는 게시 중인 후불 요금제 수입니다. 나머지 지표는 예시 값입니다.</p>
      </div>
    </section>
  )
}

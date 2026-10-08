import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import type { NetworkCode } from '../api/types'
import { won } from '../lib/format'
import { BrandChip } from '../lib/partners'
import { useApi } from '../lib/useApi'

// 슬라이더 단계 (null = 무제한). 기본값 11GB · SKT (목업 · 기획서 3.2)
const STEPS: (number | null)[] = [1, 2, 3, 5, 7, 11, 30, null]
const DEFAULT_STEP = 5
const NETWORKS: { code: NetworkCode; label: string }[] = [
  { code: 'SKT', label: 'SKT망' },
  { code: 'KT', label: 'KT망' },
  { code: 'LGU', label: 'LGU+망' },
]

const stepLabel = (gb: number | null) => (gb == null ? '무제한' : `${gb}GB`)

/** 요금 계산기 — 게시 중인 후불 요금제 중 조건에 맞는 최저가와 월 요금만 보여준다 (3사 평균 비교 없음). */
export default function Calculator() {
  const [step, setStep] = useState(DEFAULT_STEP)
  const [network, setNetwork] = useState<NetworkCode>('SKT')
  const gb = STEPS[step]
  const { data: plan, loading, error } = useApi((signal) => api.cheapest(network, gb, signal), [network, gb])
  const networkLabel = NETWORKS.find((n) => n.code === network)!.label

  return (
    <div className="calc-card" id="calc">
      <div className="calc-row">
        <label className="calc-label" htmlFor="dataSlider">
          월 데이터 사용량
        </label>
        <span className="calc-value" aria-live="polite">
          {stepLabel(gb)}
        </span>
      </div>
      <input
        type="range"
        id="dataSlider"
        min={0}
        max={STEPS.length - 1}
        step={1}
        value={step}
        aria-label="월 데이터 사용량"
        aria-valuetext={stepLabel(gb)}
        onChange={(e) => setStep(Number(e.target.value))}
      />
      <div className="range-scale" aria-hidden="true">
        <span>1GB</span>
        <span>7GB</span>
        <span>무제한</span>
      </div>

      <div className="carrier-toggle" role="group" aria-label="통신망 선택">
        {NETWORKS.map((n) => (
          <button
            key={n.code}
            type="button"
            className={n.code === network ? 'active' : ''}
            aria-pressed={n.code === network}
            onClick={() => setNetwork(n.code)}
          >
            {n.label}
          </button>
        ))}
      </div>

      <div className="price-reveal" aria-live="polite" aria-busy={loading}>
        <div>
          <div className="txt">im010 최저가 요금제 · {networkLabel}</div>
          <div className="price num">
            <span>월</span> {plan ? won(plan.monthlyPrice) : '—'}
            <span>원</span>
          </div>
        </div>
        {plan ? (
          <Link className="result-plan" to={`/plans/${plan.id}`}>
            <BrandChip code={plan.partnerCode} name={plan.partnerName} />
            <span className="result-name">{plan.dataText}</span>
            <span className="result-go">요금제 보기 →</span>
          </Link>
        ) : (
          !loading && (
            <span className="result-plan">
              <span className="result-name">{error ? '잠시 후 다시 시도해 주세요' : '조건에 맞는 요금제가 없어요'}</span>
            </span>
          )
        )}
      </div>
      <p className="calc-note">
        ※ 게시 중인 후불 요금제(제휴사 공시 요금)에서 조건에 맞는 최저가를 보여드려요. 선불 요금제는 비교에 포함하지 않아요.
      </p>
    </div>
  )
}

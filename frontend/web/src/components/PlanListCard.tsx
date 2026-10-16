import { Link } from 'react-router-dom'
import type { PlanSummary } from '../api/types'
import { won } from '../lib/format'
import { BrandChip } from '../lib/partners'

const CallIcon = () => (
  <svg viewBox="0 0 24 24" aria-hidden="true">
    <path d="M6.6 10.8a15.1 15.1 0 0 0 6.6 6.6l2.2-2.2a1 1 0 0 1 1-.25 11.4 11.4 0 0 0 3.6.57 1 1 0 0 1 1 1V20a1 1 0 0 1-1 1A17 17 0 0 1 3 4a1 1 0 0 1 1-1h3.5a1 1 0 0 1 1 1c0 1.25.2 2.45.57 3.57a1 1 0 0 1-.25 1z" />
  </svg>
)
const SmsIcon = () => (
  <svg viewBox="0 0 24 24" aria-hidden="true">
    <path d="M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zm0 4.2V18h16V8.2l-8 5-8-5zM4.5 6l7.5 4.7L19.5 6z" />
  </svg>
)
const NetIcon = () => (
  <svg viewBox="0 0 24 24" aria-hidden="true">
    <rect x="4" y="14" width="3.2" height="6" rx="1" />
    <rect x="10.4" y="9" width="3.2" height="11" rx="1" />
    <rect x="16.8" y="4" width="3.2" height="16" rx="1" />
  </svg>
)

const unlimitedVoice = (v: string | null) => !!v && (v.includes('무제한') || v.replace(/\s/g, '') === '기본제공')

/** 오른쪽 위 배지 (최대 2개): 할인 기간 · 통화 무제한 · 5G · 데이터 무제한 */
function badges(p: PlanSummary): [string, string][] {
  const out: [string, string][] = []
  if (p.discountMonths) out.push(['coral', `${p.discountMonths}개월 할인`])
  if (unlimitedVoice(p.voice)) out.push(['navy', '통화 무제한'])
  if (p.generation === '5G') out.push(['pink', '5G'])
  if (p.unlimited) out.push(['green', '데이터 무제한'])
  return out.slice(0, 2)
}

/** "월 7GB + 1Mbps" — 데이터 제공량 + 다 쓴 뒤 속도 (선불은 "월" 없이) */
function headline(p: PlanSummary): string {
  if (p.unlimited) return '데이터 무제한'
  // "데이터 0GB + 매일 5GB" 처럼 기본 제공량이 0 으로 수집된 경우 "매일 5GB" 만
  const data = p.dataText.replace(/^데이터\s*/, '').replace(/^0\s*GB\s*\+\s*/i, '')
  const monthly = p.planType === 'POSTPAID' && /^\d/.test(data)
  return `${monthly ? '월 ' : ''}${data}${p.qos ? ' + ' + p.qos : ''}`
}

/**
 * 요금제 리스트 카드 (im010_요금제리스트_목업.html): 위 정보 영역 + 점선 + 아래 할인 안내.
 * 제휴사별 요금제 · 이달의 요금제비교 · 선불요금제가 같은 카드를 쓴다. 누르면 S2 요금제 상세.
 */
export function PlanListCard({ plan: p }: { plan: PlanSummary }) {
  const prepaid = p.planType === 'PREPAID'
  const after = !prepaid && p.discountMonths && p.priceAfterDiscount != null ? p.priceAfterDiscount : null
  const saved = after != null && p.monthlyPrice != null ? (after - p.monthlyPrice) * (p.discountMonths ?? 0) : 0
  const priceLabel = prepaid ? `충전 ${won(p.chargePrice)}원` : `월 ${won(p.monthlyPrice)}원`
  return (
    <Link className="lp-card" to={`/plans/${p.id}`} aria-label={`${p.partnerName} ${p.name}, ${priceLabel}`}>
      <div className="lp-main">
        <div className="lp-top">
          <div className="lp-top-left">
            <BrandChip code={p.partnerCode} name={p.partnerName} />
            {p.networkLabel} 망 · {p.generation}
          </div>
          <div className="lp-badges">
            {badges(p).map(([tone, label]) => (
              <span key={label} className={`lp-badge ${tone}`}>
                {label}
              </span>
            ))}
          </div>
        </div>
        <p className="lp-name">{p.name}</p>
        <p className="lp-headline">{headline(p)}</p>
        <p className="lp-specs">
          <span>
            <CallIcon />
            {p.voice ?? '–'}
          </span>
          <span>
            <SmsIcon />
            {p.sms ?? '–'}
          </span>
          <span>
            <NetIcon />
            {p.generation}
          </span>
        </p>
        {prepaid ? (
          <div className="lp-price">
            <b className="num">{won(p.chargePrice)}원</b>
            <small>{p.validDays ? `충전 · ${p.validDays}일 사용` : '충전 금액'}</small>
          </div>
        ) : (
          <div className="lp-price">
            <b className="num">월 {won(p.monthlyPrice)}원</b>
            <small>{after != null ? `${p.discountMonths}개월 이후 ${won(after)}원` : '할인 기간 없음'}</small>
          </div>
        )}
      </div>
      <div className="lp-foot">
        {saved > 0 ? (
          <span className="lp-save">
            할인으로 최대 <em>{won(saved)}원</em> 아껴요
          </span>
        ) : (
          <span className="lp-save">요금제 자세히 보기 ›</span>
        )}
      </div>
    </Link>
  )
}

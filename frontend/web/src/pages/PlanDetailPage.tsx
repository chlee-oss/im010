import { Link, useParams, useSearchParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import { PlanTile } from '../components/PlanCard'
import StateMessage from '../components/StateMessage'
import { dotDate, planPrice, planPriceUnit, won } from '../lib/format'
import { BrandChip } from '../lib/partners'
import { useApi } from '../lib/useApi'
import { usePageTitle } from '../lib/usePageTitle'
import NotFoundPage from './NotFoundPage'

/** S2 요금제 상세 — 개통하기는 /go/{id} 를 거쳐 제휴사 개통 URL로 포워딩 (RC-01 접수 기록). */
export default function PlanDetailPage() {
  const id = Number(useParams().id)
  const [search] = useSearchParams()
  const { data, loading, error } = useApi((signal) => api.planDetail(id, signal), [id])
  usePageTitle(data ? `${data.plan.partnerName} ${data.plan.name}` : undefined)

  if (!Number.isInteger(id) || (error instanceof ApiError && error.status === 404)) return <NotFoundPage />
  if (!data) {
    return (
      <section className="detail">
        <div className="wrap">
          <StateMessage loading={loading} error={error} />
        </div>
      </section>
    )
  }

  const { plan } = data
  const ended = data.ended || search.get('ended') === '1'
  const prepaid = plan.planType === 'PREPAID'
  const specs = [
    { label: '데이터', value: plan.dataText.replace(/^(데이터|선불)\s*/, '') },
    { label: '통화', value: data.voice },
    { label: '문자', value: data.sms },
    { label: 'QoS', value: plan.qos },
  ]

  return (
    <section className="detail">
      <div className="wrap">
        <nav className="breadcrumb" aria-label="현재 위치">
          <Link to="/">홈</Link> › <a href="/#partners">요금제</a> › {plan.partnerName} › {plan.name}
        </nav>

        {ended && <div className="notice ended">판매가 종료된 요금제예요. 아래 비슷한 요금제를 확인해 보세요.</div>}
        {search.get('forward') === 'unavailable' && (
          <div className="notice">개통 페이지를 준비 중이에요. 잠시 후 다시 시도해 주세요.</div>
        )}

        <div className="detail-grid">
          <div className="detail-main">
            <BrandChip code={plan.partnerCode} name={plan.partnerName} />
            <h1 className="detail-title">{plan.name}</h1>
            <p className="detail-meta">
              {plan.dataText} · {plan.networkLabel}망 · {plan.generation}
              {prepaid && plan.validDays ? ` · ${plan.validDays}일` : ''}
            </p>
            <div className="tags">
              {plan.tags.map((t) => (
                <span className="tag" key={t}>
                  {t}
                </span>
              ))}
            </div>

            <div className="spec-grid">
              {specs.map((s) => (
                <div className="spec" key={s.label}>
                  <div className="spec-lbl">{s.label}</div>
                  <div className="spec-val">{s.value || '–'}</div>
                </div>
              ))}
            </div>

            <h2 className="detail-h">요금 상세</h2>
            <p className="detail-text">
              {prepaid ? '충전 금액' : '월 요금'} {won(planPrice(plan))}원
              {data.discountMonths && data.priceAfterDiscount
                ? ` · ${data.discountMonths}개월 할인 후 ${won(data.priceAfterDiscount)}원`
                : ''}
            </p>
            {data.basisDate && <p className="detail-basis">※ {dotDate(data.basisDate)} 기준 {plan.partnerName} 공시 내용</p>}
          </div>

          <aside className="price-box" aria-label="개통">
            <div className="price-box-price num">
              <span>{prepaid ? '' : '월 '}</span>
              {won(planPrice(plan))}
              <span>{planPriceUnit(plan).replace(' / 월', '')}</span>
            </div>
            {ended ? (
              <span className="btn-primary disabled" aria-disabled="true">
                판매 종료
              </span>
            ) : (
              <a className="btn-primary" href={data.activatePath} target="_blank" rel="noopener noreferrer">
                개통하기 ↗
              </a>
            )}
            <p className="price-box-note">{plan.partnerName} 사이트로 이동해요</p>
          </aside>
        </div>

        {!prepaid && data.similar.length > 0 && (
          <>
            <h2 className="detail-h">비슷한 요금제</h2>
            <div className="similar-grid">
              {data.similar.map((p) => (
                <PlanTile key={p.id} plan={p} />
              ))}
            </div>
          </>
        )}
      </div>
    </section>
  )
}

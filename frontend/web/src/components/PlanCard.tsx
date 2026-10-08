import { Link } from 'react-router-dom'
import type { PlanSummary } from '../api/types'
import { planPrice, planPriceUnit, won } from '../lib/format'
import { BrandChip } from '../lib/partners'

/** 이달의 요금제 · 선불 요금제 카드. 클릭하면 S2 상세. (배지는 프로모션관리 보류 중이라 표시 안 함) */
export function PlanCard({ plan }: { plan: PlanSummary }) {
  return (
    <div className="plan-card">
      <BrandChip code={plan.partnerCode} name={plan.partnerName} />
      <div className="data">{plan.dataText}</div>
      <div className="price-line num">
        {won(planPrice(plan))}
        <span>{planPriceUnit(plan)}</span>
      </div>
      <div className="tags">
        {plan.tags.map((t) => (
          <span className="tag" key={t}>
            {t}
          </span>
        ))}
      </div>
      <Link className="card-cta" to={`/plans/${plan.id}`}>
        요금제 자세히 보기
      </Link>
    </div>
  )
}

/** 제휴사별 요금제 그리드의 작은 카드. */
export function PlanTile({ plan }: { plan: PlanSummary }) {
  return (
    <Link className="p-card" to={`/plans/${plan.id}`}>
      <BrandChip code={plan.partnerCode} name={plan.partnerName} />
      <div className="data">{plan.dataText}</div>
      <div className="price-line num">
        {won(planPrice(plan))}
        <span>{planPriceUnit(plan)}</span>
      </div>
      <div className="meta">
        {plan.networkLabel} · {plan.generation}
      </div>
    </Link>
  )
}

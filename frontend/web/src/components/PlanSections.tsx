import { useMemo } from 'react'
import { api } from '../api/client'
import { uniquePlans } from '../lib/planRules'
import { useApi } from '../lib/useApi'
import { PlanListCard } from './PlanListCard'
import StateMessage from './StateMessage'

/** 선불요금제 — 카드만, 어떤 비교에도 넣지 않는다. */
export function PrepaidPlans() {
  const prepaid = useApi((signal) => api.plans('PREPAID', undefined, signal), [])
  const { loading, error } = prepaid
  const data = useMemo(() => (prepaid.data ? uniquePlans(prepaid.data) : null), [prepaid.data])
  return (
    <section id="prepaid">
      <div className="wrap">
        <div className="sec-head">
          <div>
            <h2>선불요금제를 알려 드릴게요</h2>
            <div className="sub">약정 없이 쓰는 선불 요금제 · 후불 요금제와 비교하지 않아요</div>
          </div>
        </div>
        <StateMessage loading={loading && !data} error={error} empty={data?.length === 0} />
        <div className="lp-pair">
          {data?.map((p) => (
            <PlanListCard key={p.id} plan={p} />
          ))}
        </div>
      </div>
    </section>
  )
}

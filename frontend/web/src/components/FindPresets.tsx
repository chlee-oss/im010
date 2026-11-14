import { useMemo } from 'react'
import { api } from '../api/client'
import { pickFindPresets } from '../lib/weeklyTabs'
import { useApi } from '../lib/useApi'
import { PlanListCard } from './PlanListCard'
import StateMessage from './StateMessage'

/**
 * 내가 찾는 요금제를 직접 찾아요 (/find) — 조건별 바로가기.
 * 규칙은 lib/weeklyTabs.js 의 pickFindPresets (미리 렌더링과 같은 파일). 후불 요금제만, 조건마다 월 요금 낮은 순 5개.
 */
export function FindPresets() {
  const { data, loading, error } = useApi((signal) => api.plans('POSTPAID', undefined, signal), [])
  const groups = useMemo(() => (data ? pickFindPresets(data) : null), [data])

  return (
    <section id="find-presets">
      <div className="wrap">
        <div className="sec-head">
          <div>
            <h2>조건별로 골라 봤어요</h2>
            <div className="sub">후불 요금제를 조건마다 월 요금이 낮은 순으로 모았어요</div>
          </div>
        </div>
        <nav className="find-jump" aria-label="조건 바로가기">
          {groups?.map((g) => (
            <a key={g.key} href={`#find-${g.key}`}>
              {g.label}
            </a>
          ))}
        </nav>
        <StateMessage loading={loading && !groups} error={error} empty={groups?.length === 0} />
        {groups?.map((g) => (
          <div key={g.key} id={`find-${g.key}`} className="find-group">
            <h3>
              {g.label} <span className="muted">{g.desc}</span>
            </h3>
            <div className="lp-pair">
              {g.plans.map((p) => (
                <PlanListCard key={p.id} plan={p} />
              ))}
            </div>
          </div>
        ))}
      </div>
    </section>
  )
}

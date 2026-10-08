import { useState } from 'react'
import { api } from '../api/client'
import { won } from '../lib/format'
import { useApi } from '../lib/useApi'
import StateMessage from './StateMessage'

const CHIP: Record<string, string> = { SKT: 'skt', KT: 'kt', LGU: 'lgu' }
const TABS = [
  { key: 'all', label: '전체' },
  { key: 'SINGLE', label: '인터넷 단독' },
  { key: 'BUNDLE', label: '인터넷 + TV 결합' },
] as const

export function InternetBanner() {
  return (
    <section id="internet">
      <div className="wrap">
        <div className="internet-banner">
          <div>
            <h3>인터넷도 im010에서 한 번에</h3>
            <p>SKT · KT · LG U+ 인터넷 상품을 비교하고, 마음에 드는 상품은 제휴업체 신청 페이지로 바로 연결해 드려요.</p>
            <a className="btn-primary" href="#internet-compare">
              인터넷 상품 보러가기
            </a>
          </div>
        </div>
      </div>
    </section>
  )
}

/** 인터넷 상품 비교 — 상담 신청은 /go/internet/{id} 를 거쳐 제휴업체 신청 페이지로 포워딩. */
export function InternetProducts() {
  const [tab, setTab] = useState<(typeof TABS)[number]['key']>('all')
  const { data, loading, error } = useApi((signal) => api.internetProducts(undefined, signal), [])
  const list = (data ?? []).filter((p) => tab === 'all' || p.productType === tab)

  return (
    <section id="internet-compare">
      <div className="wrap">
        <div className="sec-head">
          <div>
            <h2>인터넷 상품 비교</h2>
            <div className="sub">SKT · KT · LG U+ 인터넷 상품을 통신사별로 비교해보세요</div>
          </div>
        </div>
        <div className="partner-tabs" role="tablist" aria-label="인터넷 상품 유형">
          {TABS.map((t) => (
            <button
              key={t.key}
              type="button"
              role="tab"
              className={tab === t.key ? 'active' : ''}
              aria-selected={tab === t.key}
              onClick={() => setTab(t.key)}
            >
              {t.label}
            </button>
          ))}
        </div>
        <StateMessage loading={loading && !data} error={error} empty={!!data && list.length === 0} />
        <div className="isp-grid">
          {list.map((p) => (
            <div className="isp-card" key={p.id}>
              <span className={`isp-chip ${CHIP[p.carrier]}`}>{p.carrierLabel}</span>
              <span className="isp-type">{p.productType === 'SINGLE' ? '인터넷 단독' : '인터넷 + TV'}</span>
              <h4>{p.name}</h4>
              <div className="price-line num">
                {won(p.monthlyPrice)}
                <span>원 / 월</span>
              </div>
              <ul className="isp-bullets">
                {p.benefits.map((b) => (
                  <li key={b}>{b}</li>
                ))}
              </ul>
              <a className="card-cta" href={p.applyPath} target="_blank" rel="noopener noreferrer">
                상담 신청 ↗
              </a>
            </div>
          ))}
        </div>
        <p className="isp-disclaimer">
          ※ 상품명, 요금, 사은품 조건은 통신사 정책에 따라 변경될 수 있으며, 신청 · 상담은 제휴업체 페이지에서 진행됩니다.
        </p>
      </div>
    </section>
  )
}

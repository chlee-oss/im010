import { useEffect, useState, type CSSProperties } from 'react'
import { api } from '../api/client'
import type { InternetProduct } from '../api/types'
import { won } from '../lib/format'
import { outboundPath } from '../lib/outbound'
import { useApi } from '../lib/useApi'
import StateMessage from './StateMessage'
import Icon from './Icon'

const CHIP: Record<string, string> = { SKT: 'skt', KT: 'kt', LGU: 'lgu', SKB: 'skb' }
const TABS = [
  { key: 'all', label: '전체' },
  { key: 'SINGLE', label: '인터넷 단독' },
  { key: 'BUNDLE', label: '인터넷 + TV 결합' },
] as const

/** 단독 · 결합은 줄을 나눠 보여 준다 */
const GROUPS = [
  { type: 'SINGLE', label: '인터넷 단독' },
  { type: 'BUNDLE', label: '인터넷 + TV 결합' },
] as const

/** PC 한 줄에 보이는 카드 수 — 이보다 많으면(4개 이상) 롤링 */
const PC_VISIBLE = 3
const ROLL_MS = 4000

export function InternetBanner() {
  return (
    <section id="internet">
      <div className="wrap">
        <div className="internet-banner">
          <div>
            <h3>인터넷도 im010에서 한 번에</h3>
            <p>SKT · KT · LG U+ · SKB(알뜰) 인터넷 상품을 비교하고, 마음에 드는 상품은 아정당 신청 페이지로 바로 연결해 드려요.</p>
            <a className="btn-primary" href="#internet-compare">
              인터넷 상품 보러가기
            </a>
          </div>
        </div>
      </div>
    </section>
  )
}

/** 인터넷 상품 비교 — 상담 신청은 이동 안내 화면(/out) → /go/internet/{id} 를 거쳐 아정당 신청 페이지로 포워딩 (현재 인터넷 상품은 모두 아정당에서 수집). */
export function InternetProducts() {
  const [tab, setTab] = useState<(typeof TABS)[number]['key']>('all')
  const { data, loading, error } = useApi((signal) => api.internetProducts(undefined, signal), [])
  const list = (data ?? []).filter((p) => tab === 'all' || p.productType === tab)
  const groups = GROUPS.map((g) => ({ ...g, items: list.filter((p) => p.productType === g.type) })).filter(
    (g) => g.items.length > 0,
  )

  return (
    <section id="internet-compare">
      <div className="wrap">
        <div className="sec-head">
          <div>
            <h2>인터넷 상품 비교</h2>
            <div className="sub">SKT · KT · LG U+ · SKB(알뜰) 인터넷 상품을 통신사별로 비교해보세요</div>
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
        {groups.map((g) => (
          <div className="isp-group" key={g.type}>
            {tab === 'all' && <h3 className="isp-group-title">{g.label}</h3>}
            <IspRow items={g.items} label={g.label} />
          </div>
        ))}
        <p className="isp-disclaimer">
          ※ 상품명, 요금, 사은품 조건은 통신사 정책에 따라 변경될 수 있으며, 신청 · 상담은 아정당 페이지에서 진행됩니다.
        </p>
      </div>
    </section>
  )
}

/**
 * 단독 · 결합 한 줄.
 * PC: 카드 3개씩 보이고, 4개 이상이면 롤링 (4초마다 한 칸씩 자동으로 넘어가고 끝에서 처음으로, 좌우 버튼,
 * 마우스를 올리거나 포커스가 있으면 멈춤, 움직임 줄이기 설정이면 자동 넘김 없음).
 * 모바일(920px 이하): 2개씩 줄 바꿈 — 홀수면 마지막 카드 하나만 남고 다음 묶음은 새 줄에서 시작 (CSS).
 */
function IspRow({ items, label }: { items: InternetProduct[]; label: string }) {
  const [index, setIndex] = useState(0)
  const [paused, setPaused] = useState(false)
  const rolling = items.length > PC_VISIBLE
  // 한 칸씩 넘기다 마지막 카드가 보이면 다음은 처음으로 (처음에서 이전이면 끝으로)
  const last = Math.max(0, items.length - PC_VISIBLE)
  const go = (dir: 1 | -1) => setIndex((i) => (dir === 1 ? (i >= last ? 0 : i + 1) : i <= 0 ? last : i - 1))

  useEffect(() => {
    if (!rolling || paused) return
    const pc = window.matchMedia('(min-width: 921px)')
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)')
    if (!pc.matches || reduce.matches) return
    const t = window.setInterval(() => setIndex((i) => (i >= last ? 0 : i + 1)), ROLL_MS)
    return () => window.clearInterval(t)
  }, [rolling, paused, last])

  return (
    <div
      className={rolling ? 'isp-row rolling' : 'isp-row'}
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
    >
      {rolling && (
        <button type="button" className="isp-roll prev" aria-label={`${label} 이전 상품`} onClick={() => go(-1)}>
          <Icon name="chevron_left" />
        </button>
      )}
      <div className="isp-viewport">
        <div className="isp-track" style={rolling ? ({ '--i': index } as CSSProperties) : undefined}>
          {items.map((p) => (
            <IspCard key={p.id} p={p} />
          ))}
        </div>
      </div>
      {rolling && (
        <button type="button" className="isp-roll next" aria-label={`${label} 다음 상품`} onClick={() => go(1)}>
          <Icon name="chevron_right" />
        </button>
      )}
    </div>
  )
}

function IspCard({ p }: { p: InternetProduct }) {
  return (
    <div className="isp-card">
      <div>
        <span className={`isp-chip ${CHIP[p.carrier]}`}>{p.carrierLabel}</span>
        <span className="isp-type">{p.productType === 'SINGLE' ? '인터넷 단독' : '인터넷 + TV'}</span>
      </div>
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
      <a className="card-cta" href={outboundPath('internet', p.id, p.applyPath, p.productType === 'SINGLE')} target="_blank" rel="noopener noreferrer">
        상담 신청 <Icon name="arrow_outward" />
      </a>
    </div>
  )
}

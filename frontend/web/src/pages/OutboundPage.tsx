import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import { planPrice, won } from '../lib/format'
import { INTERNET_PARTNER, OUTBOUND_DELAY_MS, safeGoPath } from '../lib/outbound'
import { BrandChip } from '../lib/partners'
import { useApi } from '../lib/useApi'
import { usePageTitle } from '../lib/usePageTitle'
import NotFoundPage from './NotFoundPage'


/**
 * 제휴사 · 협력사 페이지로 이동하기 전 안내 화면 (/out?kind=plan|internet&id=&to=/go/…).
 * im010 ≫ 제휴사(인터넷은 아정당) · 요금제(상품) · 요금 · 진행 막대를 보여 주고 잠시 뒤 /go 로 넘어간다. 검색엔진에는 노출하지 않는다.
 */
export default function OutboundPage() {
  const [search] = useSearchParams()
  const to = safeGoPath(search.get('to'))
  const kind = search.get('kind') === 'internet' ? 'internet' : 'plan'
  const id = Number(search.get('id'))
  const plan = useApi((signal) => (kind === 'plan' && id ? api.planDetail(id, signal) : Promise.resolve(null)), [kind, id])
  const internet = useApi(
    (signal) => (kind === 'internet' ? api.internetProducts(undefined, signal) : Promise.resolve(null)),
    [kind],
  )
  const [started] = useState(() => Date.now())
  // 잘못된 주소면 404 화면이 제목을 정하도록 둔다 (부모 effect 가 나중에 실행돼 덮어쓰지 않게)
  usePageTitle(to ? '이동 중' : '페이지를 찾을 수 없음')

  useEffect(() => {
    const meta = document.createElement('meta')
    meta.name = 'robots'
    meta.content = 'noindex, nofollow'
    document.head.appendChild(meta)
    return () => meta.remove()
  }, [])

  // 안내 정보를 못 불러와도 이동은 한다
  useEffect(() => {
    if (!to) return
    const t = window.setTimeout(() => window.location.replace(to), Math.max(0, OUTBOUND_DELAY_MS - (Date.now() - started)))
    return () => window.clearTimeout(t)
  }, [to, started])

  if (!to) return <NotFoundPage />

  const p = plan.data?.plan
  const product = internet.data?.find((x) => x.id === id)
  const partnerName = kind === 'internet' ? INTERNET_PARTNER : p?.partnerName
  const title = p?.name ?? product?.name
  const price = p
    ? p.planType === 'PREPAID'
      ? `${won(planPrice(p))}원${p.validDays ? ` / ${p.validDays}일` : ''}`
      : `월 ${won(planPrice(p))}원`
    : product
      ? `월 ${won(product.monthlyPrice)}원`
      : null

  return (
    <div className="outbound" role="status" aria-live="polite">
      <div className="outbound-card">
        <div className="outbound-route">
          <span className="logo">
            im<span>010</span>
          </span>
          <span className="outbound-arrow" aria-hidden="true">
            ≫
          </span>
          {p ? (
            <BrandChip code={p.partnerCode} name={p.partnerName} />
          ) : kind === 'internet' ? (
            <span className="brand-chip outbound-ajd">{INTERNET_PARTNER}</span>
          ) : (
            <span className="outbound-partner-blank" />
          )}
        </div>

        <p className="outbound-title">
          {title ?? ' '}
          {price && <span className="num"> | {price}</span>}
        </p>

        <div className="outbound-bar" aria-hidden="true">
          <span style={{ animationDuration: `${OUTBOUND_DELAY_MS}ms` }} />
        </div>

        <p className="outbound-msg">
          {kind === 'plan'
            ? `${partnerName ?? '제휴사'} 공식 홈페이지로 이동 중입니다.`
            : `${INTERNET_PARTNER} 인터넷 상품 신청 페이지로 이동 중입니다.`}
          <br />
          잠시만 기다려 주세요.
        </p>
        <a className="outbound-now" href={to}>
          바로 이동하기 →
        </a>

        <p className="outbound-note">
          {kind === 'plan'
            ? `im010은 요금제 비교 정보를 제공하며, 가입 · 개통은 ${partnerName ?? '제휴사'}에서 진행됩니다.`
            : `im010은 인터넷 상품 비교 정보를 제공하며, 신청 · 상담은 ${INTERNET_PARTNER}에서 진행됩니다.`}
        </p>
      </div>
    </div>
  )
}

import { useEffect, type CSSProperties } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import { AiCard, AiDefaultThumb } from '../components/AiCard'
import { aiDate, aiPostPath } from '../lib/aiFormat'
import { PlanCard } from '../components/PlanCard'
import StateMessage from '../components/StateMessage'
import { won } from '../lib/format'
import { useApi } from '../lib/useApi'
import { usePageTitle } from '../lib/usePageTitle'
import NotFoundPage from './NotFoundPage'

/** 같은 글은 하루 한 번만 조회 수에 센다 (이 브라우저 기준) */
function countView(id: number) {
  const key = 'im010.aiView.' + id
  const today = new Date().toISOString().slice(0, 10)
  try {
    if (localStorage.getItem(key) === today) return
    localStorage.setItem(key, today)
  } catch {
    // 저장소를 못 쓰면 그냥 센다
  }
  void api.aiView(id)
}

/** 알뜰AI 상세 (/ai/posts/{id}/{slug}) — 목업 ② */
export default function AiPostPage() {
  const { id, slug } = useParams()
  const postId = Number(id)
  const { data, loading, error } = useApi((signal) => api.aiPost(postId, signal), [postId])
  const p = data?.post
  usePageTitle(p ? p.seoTitle || p.title : '알뜰AI')

  useEffect(() => {
    if (p) countView(p.id)
  }, [p])

  useEffect(() => {
    if (!p) return
    const meta = document.querySelector<HTMLMetaElement>('meta[name="description"]')
    const before = meta?.content
    if (meta) meta.content = p.seoDescription || p.cardSummary || p.aiSummary.join(' ') || p.title
    window.scrollTo(0, 0)
    return () => {
      if (meta && before !== undefined) meta.content = before
    }
  }, [p])

  if (!Number.isInteger(postId) || (error instanceof ApiError && error.status === 404)) return <NotFoundPage />
  if (!data || !p) {
    return (
      <section className="ai-page">
        <div className="wrap narrow">
          <StateMessage loading={loading} error={error} />
        </div>
      </section>
    )
  }
  // 주소의 이름 부분이 바뀌었거나 빠졌으면 지금 주소로 (검색 노출 기준 주소 하나)
  if (slug !== p.slug) return <Navigate to={aiPostPath(p)} replace />

  const style = { '--ai-bg': p.bgColor, '--ai-ac': p.accentColor } as CSSProperties
  const sources = p.sources.filter((s) => s.name || s.title)
  return (
    <section className="ai-page ai-detail" style={style}>
      <article className="wrap narrow">
        <nav className="ai-crumb" aria-label="위치">
          <Link to="/ai">알뜰AI</Link> › <Link to={`/ai/${p.categorySlug}`}>{p.categoryName}</Link>
        </nav>
        <h1>{p.title}</h1>
        <p className="ai-meta">
          {p.sponsored && <span className="ai-ad">제휴</span>}
          <b>{p.categoryName}</b> · {aiDate(p.publishedAt)} · {p.readMinutes}분 읽기
          {p.contentUpdatedAt && ` · 수정 ${aiDate(p.contentUpdatedAt)}`} · {p.authorName}
        </p>

        {p.aiSummary.length > 0 && (
          <div className="ai-summary">
            <h2>
              ✨ AI 3줄 요약 <small>AI가 정리하고 편집팀이 확인했어요</small>
            </h2>
            <ul>
              {p.aiSummary.map((s) => (
                <li key={s}>{s}</li>
              ))}
            </ul>
          </div>
        )}

        <div className="ai-hero">
          {p.thumbnailUrl ? <img src={p.thumbnailUrl} alt="" /> : <AiDefaultThumb slug={p.categorySlug} accent={p.accentColor} />}
        </div>

        {/* 서버에서 허용한 태그만 남긴 HTML */}
        <div className="ai-body" dangerouslySetInnerHTML={{ __html: p.bodyHtml }} />

        <p className="ai-note">
          {aiDate(p.contentUpdatedAt ?? p.publishedAt)} 기준 정보예요. 요금 · 혜택은 바뀔 수 있으니 최신 요금은 <a href="/#calc">요금제 비교하기</a>에서 확인하세요.
        </p>
        {sources.length > 0 && (
          <ul className="ai-sources">
            {sources.map((s, i) => (
              <li key={i}>
                출처: {s.name} {s.title && `「${s.title}」`} {s.date && `(${s.date})`}{' '}
                {s.url && (
                  <a href={s.url} target="_blank" rel="noopener noreferrer nofollow">
                    원문 ↗
                  </a>
                )}
              </li>
            ))}
          </ul>
        )}
        {p.tags.length > 0 && (
          <div className="ai-tags">
            {p.tags.map((t) => (
              <Link key={t} to={`/ai?tag=${encodeURIComponent(t)}`}>
                #{t}
              </Link>
            ))}
          </div>
        )}

        {data.relatedPlans.length > 0 && (
          <div className="ai-related">
            <h2>이 글과 관련된 요금제</h2>
            <div className="ai-plans">
              {data.relatedPlans.map((plan) => (
                <PlanCard key={plan.id} plan={plan} />
              ))}
            </div>
          </div>
        )}
        {data.relatedInternet.length > 0 && (
          <div className="ai-related">
            <h2>이 글과 관련된 인터넷 상품</h2>
            <div className="ai-plans">
              {data.relatedInternet.map((x) => (
                <div className="isp-card" key={x.id}>
                  <div>
                    <span className={`isp-chip ${x.carrier.toLowerCase()}`}>{x.carrierLabel}</span>
                    <span className="isp-type">{x.productType === 'SINGLE' ? '인터넷 단독' : '인터넷 + TV'}</span>
                  </div>
                  <h4>{x.name}</h4>
                  <div className="price-line num">
                    {won(x.monthlyPrice)}
                    <span>원 / 월</span>
                  </div>
                  <a className="card-cta" href={x.applyPath} target="_blank" rel="noopener noreferrer">
                    상담 신청 ↗
                  </a>
                </div>
              ))}
            </div>
          </div>
        )}

        <nav className="ai-pager" aria-label="이전 · 다음 글">
          {data.older ? (
            <Link to={aiPostPath(data.older)} title={data.older.title}>
              ‹ 이전 글
            </Link>
          ) : (
            <span />
          )}
          <Link to={`/ai/${p.categorySlug}`}>목록</Link>
          {data.newer ? (
            <Link to={aiPostPath(data.newer)} title={data.newer.title}>
              다음 글 ›
            </Link>
          ) : (
            <span />
          )}
        </nav>

        {data.sameCategory.length > 0 && (
          <div className="ai-same">
            <h2>{p.categoryName}의 다른 글</h2>
            <div className="ai-grid">
              {data.sameCategory.map((c) => (
                <AiCard key={c.id} card={c} />
              ))}
            </div>
          </div>
        )}
      </article>
    </section>
  )
}

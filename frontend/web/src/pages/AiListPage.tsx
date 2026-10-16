import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import type { AiCard as Card } from '../api/types'
import { AiCard, AiFeaturedCard } from '../components/AiCard'
import StateMessage from '../components/StateMessage'
import { useApi } from '../lib/useApi'
import { usePageTitle } from '../lib/usePageTitle'
import NotFoundPage from './NotFoundPage'

/** 알뜰AI 목록 (/ai, /ai/{카테고리}) — 탭 · 검색 · 정렬 · 상단 고정 · 카드 2열 · 더 보기 */
export default function AiListPage() {
  const { category } = useParams()
  const [params] = useSearchParams()
  // 카테고리 · 검색 · 정렬이 바뀌면 새로 그린다 (더 보기로 붙인 글 · 검색 입력 초기화)
  return <AiList key={[category, params.get('q'), params.get('tag'), params.get('sort')].join('|')} />
}

function AiList() {
  const { category } = useParams()
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const tag = params.get('tag') ?? ''
  const sort = params.get('sort') === 'popular' ? 'popular' : 'latest'
  const [input, setInput] = useState(q)
  const [page, setPage] = useState(1)
  const [more, setMore] = useState<Card[]>([])
  const [moreLoading, setMoreLoading] = useState(false)
  const categories = useApi((signal) => api.aiCategories(signal), [])
  const current = categories.data?.find((c) => c.slug === category)
  usePageTitle(current ? `${current.name} · 알뜰AI` : '알뜰AI')

  const first = useApi(
    (signal) => api.aiPosts({ category, q: q || undefined, tag: tag || undefined, sort, page: 1 }, signal),
    [category, q, tag, sort],
  )

  if (category && categories.data && !current) return <NotFoundPage />

  const setParam = (key: string, value: string) => {
    const n = new URLSearchParams(params)
    if (value) n.set(key, value)
    else n.delete(key)
    setParams(n)
  }

  const loadMore = async () => {
    setMoreLoading(true)
    try {
      const next = await api.aiPosts({ category, q: q || undefined, tag: tag || undefined, sort, page: page + 1 })
      if (next) {
        setMore((m) => [...m, ...next.items])
        setPage(page + 1)
      }
    } finally {
      setMoreLoading(false)
    }
  }

  const data = first.data
  const items = [...(data?.items ?? []), ...more]
  const query = (extra: string) => {
    const n = new URLSearchParams(params)
    n.delete('tag')
    const s = n.toString()
    return extra + (s ? '?' + s : '')
  }

  return (
    <section className="ai-page">
      <div className="wrap">
        <div className="ai-head">
          <h1>
            알뜰<em>AI</em>
          </h1>
          <p>알뜰폰 · 인터넷 소식과 통신비 아끼는 정보를 모았어요</p>
        </div>

        <div className="ai-toolbar">
          <div className="ai-tabs" role="tablist" aria-label="카테고리">
            <Link role="tab" aria-selected={!category} className={!category ? 'on' : ''} to={query('/ai')}>
              전체
            </Link>
            {(categories.data ?? []).map((c) => (
              <Link key={c.slug} role="tab" aria-selected={category === c.slug} className={category === c.slug ? 'on' : ''} to={query(`/ai/${c.slug}`)}>
                {c.name}
              </Link>
            ))}
          </div>
          <div className="ai-tools">
            <form
              role="search"
              onSubmit={(e) => {
                e.preventDefault()
                const v = input.trim()
                if (v.length === 1) return
                setParam('q', v)
              }}
            >
              <input type="search" placeholder="제목 · 태그 검색" aria-label="알뜰AI 검색" value={input} onChange={(e) => setInput(e.target.value)} maxLength={50} />
            </form>
            <select aria-label="정렬" value={sort} onChange={(e) => setParam('sort', e.target.value === 'popular' ? 'popular' : '')}>
              <option value="latest">최신순</option>
              <option value="popular">많이 본 순</option>
            </select>
          </div>
        </div>

        {(q || tag) && (
          <p className="ai-filter">
            {tag ? `#${tag}` : `"${q}"`} 검색 결과 {data ? data.total.toLocaleString() + '건' : ''}{' '}
            <button type="button" onClick={() => setParams(sort === 'popular' ? { sort } : {})}>
              ✕ 지우기
            </button>
          </p>
        )}

        {data?.pinned && <AiFeaturedCard card={data.pinned} />}
        <StateMessage loading={first.loading && !data} error={first.error} />
        {data && items.length === 0 && !data.pinned && <p className="state-msg">{q || tag ? '찾는 글이 없어요. 다른 말로 검색해 보세요.' : '아직 글이 없어요.'}</p>}

        <div className="ai-grid">
          {items.map((c) => (
            <AiCard key={c.id} card={c} />
          ))}
        </div>

        {data && items.length < data.total && (
          <button type="button" className="ai-more" onClick={loadMore} disabled={moreLoading}>
            {moreLoading ? '불러오는 중…' : `더 보기 (${items.length} / ${data.total})`}
          </button>
        )}
      </div>
    </section>
  )
}

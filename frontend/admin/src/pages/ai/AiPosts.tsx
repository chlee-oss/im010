import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Badge, Empty, Loading, PageHead, Pager, Tabs } from '../../components/ui'
import { get, qs } from '../../lib/api'
import { AI_STATE, dateTime, won } from '../../lib/format'
import { useMe } from '../../lib/me'
import type { AiCategory, AiPostPage } from '../../lib/types'
import { useLoad } from '../../lib/useLoad'

export const AI_STATE_TONE: Record<string, 'green' | 'coral' | 'amber' | 'gray'> = {
  PUBLISHED: 'green',
  DRAFT: 'coral',
  SCHEDULED: 'amber',
  HIDDEN: 'gray',
}

/** CT-01 알뜰AI 게시글관리 — 목록 */
export default function AiPosts() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const { can } = useMe()
  const category = params.get('category') ?? ''
  const state = params.get('state') ?? ''
  const page = Number(params.get('page') ?? 1)
  const [q, setQ] = useState(params.get('q') ?? '')
  const categories = useLoad(() => get<AiCategory[]>('/contents/categories'), [])
  const { data, error } = useLoad(
    () => get<AiPostPage>('/contents/posts' + qs({ category, state, q: params.get('q'), page })),
    [category, state, params.get('q'), page],
  )

  const set = (key: string, value: string) => {
    const n = new URLSearchParams(params)
    if (value) n.set(key, value)
    else n.delete(key)
    if (key !== 'page') n.delete('page')
    setParams(n)
  }

  return (
    <>
      <PageHead title="알뜰AI 게시글관리">
        {can('CT-01', 'EDIT') && (
          <button className="primary" onClick={() => navigate('/contents/posts/new' + (category ? '?category=' + category : ''))}>
            + 글쓰기
          </button>
        )}
      </PageHead>

      <div className="filters">
        <Tabs
          value={category}
          onChange={(v) => set('category', v)}
          tabs={[['', '전체'], ...(categories.data ?? []).map((c): [string, string] => [c.code, c.name + (c.exposed ? '' : ' (비노출)')])]}
        />
        <div className="filter-row">
          <label>
            상태{' '}
            <select value={state} onChange={(e) => set('state', e.target.value)}>
              <option value="">전체</option>
              {Object.entries(AI_STATE).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </label>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              set('q', q.trim())
            }}
          >
            <input placeholder="제목 · 태그 검색" value={q} onChange={(e) => setQ(e.target.value)} maxLength={50} />
            <button>검색</button>
          </form>
          <span className="muted">게시 중 · 게시 일시가 지난 글만 프런트 알뜰AI에 보입니다. 카테고리마다 상단 고정은 1개입니다.</span>
        </div>
      </div>

      {!data ? (
        <Loading error={error} />
      ) : data.items.length === 0 ? (
        <Empty>해당하는 글이 없습니다.</Empty>
      ) : (
        <>
          <table className="grid">
            <thead>
              <tr>
                <th>카테고리</th>
                <th></th>
                <th>제목</th>
                <th>상태</th>
                <th>게시 일시</th>
                <th className="num">조회</th>
                <th>수정</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map(({ post: p, state: st }) => (
                <tr key={p.id} className="clickable" onClick={() => navigate(`/contents/posts/${p.id}`)}>
                  <td>
                    <span className="chip" style={{ background: p.bgColor, color: p.accentColor }}>
                      {p.categoryName}
                    </span>
                  </td>
                  <td className="ai-thumb-cell">
                    {p.thumbnailUrl ? <img src={p.thumbnailUrl} alt="" className="ai-thumb-mini" /> : <span className="ai-thumb-mini empty" style={{ background: p.bgColor }} />}
                  </td>
                  <td>
                    <b>{p.title}</b>
                    <div className="small muted">
                      {p.pinned && <Badge tone="navy">상단 고정</Badge>} {p.sponsored && <Badge tone="amber">제휴</Badge>}{' '}
                      {(p.tags ?? '')
                        .split('|')
                        .filter(Boolean)
                        .map((t) => '#' + t)
                        .join(' ')}
                    </div>
                  </td>
                  <td>
                    <Badge tone={AI_STATE_TONE[st]}>{AI_STATE[st]}</Badge>
                  </td>
                  <td>{dateTime(p.publishAt)}</td>
                  <td className="num">{won(p.viewCount)}</td>
                  <td className="muted">
                    {p.updatedBy} · {dateTime(p.updatedAt)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pager page={data.page} total={data.total} onChange={(p) => set('page', String(p))} />
        </>
      )}
    </>
  )
}

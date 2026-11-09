import { useMemo, useState } from 'react'
import { Loading, PageHead } from '../../components/ui'
import { get } from '../../lib/api'
import { useMe } from '../../lib/me'
import type { DashContent as Data, DashPost } from '../../lib/types'
import { useLoad } from '../../lib/useLoad'
import { BarList, Card, CollectBar, DashFilter, dimRows, n, pct, seconds, useDashQuery } from './dash'

type SortKey = 'views' | 'completeRate' | 'planClicks' | 'engage'

const metric = (p: DashPost, k: SortKey) =>
  k === 'views' ? p.views : k === 'planClicks' ? p.planClicks : k === 'completeRate' ? (p.views ? p.completes / p.views : -1) : p.engageCount ? p.engageMs / p.engageCount : -1

/** DS-03 콘텐츠 효과: 알뜰AI 글별 조회 · 끝까지 읽음 · 머문 시간 · 요금제로 이동, 카테고리별, 인터넷 상품 */
export default function DashContent() {
  const { can } = useMe()
  const { q, setQ, params } = useDashQuery()
  const { data, error, setData } = useLoad(() => get<Data>('/dashboard/content' + params), [params])
  const [sort, setSort] = useState<SortKey>('views')
  const posts = useMemo(() => [...(data?.posts ?? [])].sort((a, b) => metric(b, sort) - metric(a, sort)), [data, sort])
  const th = (k: SortKey, label: string) => (
    <th className="num sortable" aria-sort={sort === k ? 'descending' : 'none'} onClick={() => setSort(k)}>
      {label}
      {sort === k ? ' ▼' : ''}
    </th>
  )
  const total = posts.reduce((s, p) => ({ views: s.views + p.views, completes: s.completes + p.completes, plan: s.plan + p.planClicks }), {
    views: 0,
    completes: 0,
    plan: 0,
  })

  return (
    <>
      <PageHead title="대시보드 · 콘텐츠 효과" />
      <DashFilter q={q} onChange={setQ} csv={can('DS-03', 'DOWNLOAD') ? '/admin/api/dashboard/content.csv' + params : undefined} />
      {!data ? (
        <Loading error={error} />
      ) : (
        <>
          <CollectBar settings={data.settings} onChange={(s) => setData({ ...data, settings: s })} />
          <div className="kpi-row">
            <div className="kpi">
              <span>알뜰AI 글 조회</span>
              <b className="num">{n(total.views)}</b>
            </div>
            <div className="kpi">
              <span>끝까지 읽은 비율</span>
              <b className="num">{pct(total.completes, total.views)}</b>
            </div>
            <div className="kpi">
              <span>글에서 요금제로 이동</span>
              <b className="num">{n(total.plan)}</b>
            </div>
            <div className="kpi">
              <span>이동률</span>
              <b className="num">{pct(total.plan, total.views)}</b>
            </div>
          </div>
          <div className="dash-grid">
            <Card title="알뜰AI 글별" note="머리글을 누르면 정렬 · 끝까지 읽음 = 본문 90% 스크롤" wide>
              {posts.length === 0 ? (
                <div className="dash-empty">아직 데이터가 없습니다</div>
              ) : (
                <div className="table-scroll">
                  <table className="grid">
                    <thead>
                      <tr>
                        <th>제목</th>
                        <th>카테고리</th>
                        {th('views', '조회')}
                        <th className="num">방문자</th>
                        {th('completeRate', '끝까지 읽음')}
                        {th('engage', '평균 머문 시간')}
                        {th('planClicks', '요금제로 이동')}
                      </tr>
                    </thead>
                    <tbody>
                      {posts.map((p) => (
                        <tr key={p.postId}>
                          <td>
                            {p.title} <span className="muted">#{p.postId}</span>
                          </td>
                          <td>{p.categoryName ?? '–'}</td>
                          <td className="num">{n(p.views)}</td>
                          <td className="num">{n(p.visitors)}</td>
                          <td className="num">
                            {n(p.completes)} <small className="muted">({pct(p.completes, p.views)})</small>
                          </td>
                          <td className="num">{seconds(p.engageMs, p.engageCount)}</td>
                          <td className="num">
                            {n(p.planClicks)} <small className="muted">({pct(p.planClicks, p.views)})</small>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Card>
            <Card title="카테고리별 조회">
              <BarList rows={data.categories.map((c) => ({ label: `${c.categoryName} (${c.posts}개 글)`, value: c.views }))} />
            </Card>
            <Card title="카테고리별 요금제로 이동">
              <BarList rows={data.categories.map((c) => ({ label: c.categoryName, value: c.planClicks, sub: pct(c.planClicks, c.views) }))} />
            </Card>
            <Card title="인터넷 상담 신청 · 통신사" note="이동 안내 화면 기준">
              <BarList rows={dimRows({ dims: data.internet }, 'INTERNET_CARRIER', (c) => c)} />
            </Card>
            <Card title="인터넷 상담 신청 · 단독 / 결합">
              <BarList rows={dimRows({ dims: data.internet }, 'INTERNET_TYPE', (c) => c)} />
            </Card>
          </div>
        </>
      )}
    </>
  )
}

import { Link } from 'react-router-dom'
import { Loading, PageHead } from '../../components/ui'
import { get } from '../../lib/api'
import { won } from '../../lib/format'
import { useMe } from '../../lib/me'
import type { DashSummary as Data } from '../../lib/types'
import { useLoad } from '../../lib/useLoad'
import { BarList, Card, CollectBar, DashFilter, DEVICE, Kpi, LineChart, PAGE, REF, n, pct, seconds, useDashQuery } from './dash'

const ENGINE: Record<string, string> = {
  naver: '네이버',
  google: '구글',
  daum: '다음',
  bing: 'Bing',
  other: '기타 검색',
  unknown: '구분 전 수집분',
}
const PLAN_TYPE: Record<string, string> = { PREPAID: '선불', POSTPAID: '후불', UNKNOWN: '(알 수 없음)' }

const share = (part: number, whole: number) => (whole ? (part * 100) / whole : 0)

/** 핵심 지표: 검색 유입 · 제휴사 이동 · 선불 비중 (직전 같은 기간과 비교) */
function KeySection({ data }: { data: Data }) {
  const { current: c, previous: p, searchEngines, searchLandings, partners, planTypes } = data.key
  return (
    <div className="dash-grid">
      <Card title="핵심 지표" note="검색 유입 · 제휴사 이동 · 선불 비중" wide>
        <div className="kpi-row">
          <Kpi label="검색 유입" value={c.searchSessions} prev={p.searchSessions} hint="구글 · 네이버 등 검색으로 들어온 세션" />
          <Kpi
            label="제휴사 이동"
            value={c.planOutbounds + c.internetOutbounds}
            prev={p.planOutbounds + p.internetOutbounds}
            hint="요금제 개통하기 + 인터넷 상담 신청 → 이동 안내 화면까지 간 횟수"
          />
          <Kpi label="요금제 개통하기" value={c.planOutbounds} prev={p.planOutbounds} />
          <Kpi label="인터넷 상담 신청" value={c.internetOutbounds} prev={p.internetOutbounds} />
          <Kpi
            label="선불 조회 비중"
            value={share(c.prepaidViews, c.planViews)}
            prev={share(p.prepaidViews, p.planViews)}
            suffix="%"
            hint="선불 요금제 상세 조회 ÷ 전체 요금제 상세 조회"
          />
          <Kpi
            label="선불 개통 비중"
            value={share(c.prepaidOutbounds, c.planOutbounds)}
            prev={share(p.prepaidOutbounds, p.planOutbounds)}
            suffix="%"
            hint="선불 요금제 개통하기 ÷ 전체 요금제 개통하기"
          />
        </div>
      </Card>
      <Card title="검색엔진별 유입" note="세션 기준">
        <BarList rows={searchEngines.map((r) => ({ label: ENGINE[r.value] ?? r.value, value: r.cnt }))} />
      </Card>
      <Card title="검색으로 처음 본 화면" note="세션 기준">
        <BarList rows={searchLandings.map((r) => ({ label: PAGE[r.value] ?? r.value, value: r.cnt }))} />
      </Card>
      <Card title="제휴사별 이동" note="요금제 개통하기 기준">
        <BarList
          rows={partners.map((r) => ({ label: r.partnerName, value: r.outbounds, sub: `조회 ${n(r.views)} · 전환 ${pct(r.outbounds, r.views)}` }))}
        />
      </Card>
      <Card title="선불 · 후불" note="요금제 조회 · 개통하기">
        {planTypes.length === 0 ? (
          <div className="dash-empty">아직 데이터가 없습니다</div>
        ) : (
          <table className="grid">
            <thead>
              <tr>
                <th>구분</th>
                <th className="num">조회</th>
                <th className="num">개통하기</th>
                <th className="num">전환율</th>
              </tr>
            </thead>
            <tbody>
              {planTypes.map((t) => (
                <tr key={t.planType}>
                  <td>{PLAN_TYPE[t.planType] ?? t.planType}</td>
                  <td className="num">{n(t.views)}</td>
                  <td className="num">{n(t.outbounds)}</td>
                  <td className="num">{pct(t.outbounds, t.views)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  )
}

/** DS-01 대시보드 요약: 핵심 지표 · 방문 · 요금제 조회 · 개통하기 · 유입 · 기기 · 화면 · 인기 요금제 · 인기 글 */
export default function DashSummary() {
  const { can } = useMe()
  const { q, setQ, params } = useDashQuery()
  const { data, error, setData } = useLoad(() => get<Data>('/dashboard/summary' + params), [params])

  return (
    <>
      <PageHead title="대시보드 · 요약" />
      <DashFilter q={q} onChange={setQ} csv={can('DS-01', 'DOWNLOAD') ? '/admin/api/dashboard/summary.csv' + params : undefined} />
      {!data ? (
        <Loading error={error} />
      ) : (
        <>
          <CollectBar settings={data.settings} onChange={(s) => setData({ ...data, settings: s })} />
          <KeySection data={data} />
          <div className="kpi-row" style={{ marginTop: 14 }}>
            <Kpi label="방문자" value={data.current.visitors} prev={data.previous.visitors} hint="날마다 센 순방문자의 합" />
            <Kpi label="신규 방문자" value={data.current.newVisitors} prev={data.previous.newVisitors} />
            <Kpi label="페이지뷰" value={data.current.pageViews} prev={data.previous.pageViews} />
            <Kpi label="요금제 조회" value={data.current.planViews} prev={data.previous.planViews} hint="요금제 상세 화면을 연 횟수" />
            <Kpi label="개통하기" value={data.current.outbounds} prev={data.previous.outbounds} hint="개통하기 → 이동 안내 화면까지 간 횟수" />
            <Kpi
              label="개통 전환율"
              value={data.current.planViews ? (data.current.outbounds * 100) / data.current.planViews : 0}
              prev={data.previous.planViews ? (data.previous.outbounds * 100) / data.previous.planViews : 0}
              suffix="%"
              hint="개통하기 ÷ 요금제 조회"
            />
          </div>
          <p className="muted small">▲▼ 는 직전 같은 기간({data.range.from} 이전 같은 일수)과 비교한 값입니다.</p>

          <div className="dash-grid">
            <Card title="일별 추이" wide>
              <LineChart
                days={data.daily.map((d) => d.day)}
                series={[
                  { label: '방문자', values: data.daily.map((d) => d.visitors), tone: 'navy' },
                  { label: '개통하기', values: data.daily.map((d) => d.outbounds), tone: 'coral' },
                ]}
              />
            </Card>
            <Card title="검색 유입 추이" note="검색으로 들어온 세션" wide>
              <LineChart
                days={data.daily.map((d) => d.day)}
                series={[{ label: '검색 유입', values: data.daily.map((d) => d.searchSessions), tone: 'navy' }]}
              />
            </Card>
            <Card title="유입 경로" note="세션 기준">
              <BarList rows={data.byRef.map((r) => ({ label: REF[r.key] ?? r.key, value: r.sessions }))} />
            </Card>
            <Card title="기기" note="방문자 기준">
              <BarList rows={data.byDevice.map((r) => ({ label: DEVICE[r.key] ?? r.key, value: r.visitors }))} />
            </Card>
            <Card title="화면별 조회" note="평균 머문 시간" wide>
              {data.pages.length === 0 ? (
                <div className="dash-empty">아직 데이터가 없습니다</div>
              ) : (
                <table className="grid">
                  <thead>
                    <tr>
                      <th>화면</th>
                      <th className="num">조회</th>
                      <th className="num">방문자</th>
                      <th className="num">평균 머문 시간</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.pages.map((p) => (
                      <tr key={p.pageType}>
                        <td>{PAGE[p.pageType] ?? p.pageType}</td>
                        <td className="num">{n(p.views)}</td>
                        <td className="num">{n(p.visitors)}</td>
                        <td className="num">{seconds(p.engageMs, p.engageCount)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </Card>
            <Card title="인기 요금제 TOP 5" note="조회 많은 순">
              {data.topPlans.length === 0 ? (
                <div className="dash-empty">아직 데이터가 없습니다</div>
              ) : (
                <ol className="rank">
                  {data.topPlans.map((p) => (
                    <li key={p.planId}>
                      <span>
                        <b>{p.partnerName ?? '–'}</b> {p.name} <span className="muted">{p.price != null ? won(p.price) : ''}</span>
                      </span>
                      <span className="num">
                        조회 {n(p.views)} · 개통 {n(p.outbounds)} <small>({pct(p.outbounds, p.views)})</small>
                      </span>
                    </li>
                  ))}
                </ol>
              )}
              {can('DS-02', 'VIEW') && (
                <Link className="more" to="/dashboard/interest">
                  관심사에서 전체 보기 →
                </Link>
              )}
            </Card>
            <Card title="인기 알뜰AI 글 TOP 5" note="조회 많은 순">
              {data.topPosts.length === 0 ? (
                <div className="dash-empty">아직 데이터가 없습니다</div>
              ) : (
                <ol className="rank">
                  {data.topPosts.map((p) => (
                    <li key={p.postId}>
                      <span>
                        {p.title} <span className="muted">{p.categoryName ?? ''}</span>
                      </span>
                      <span className="num">
                        조회 {n(p.views)} · 완독 {pct(p.completes, p.views)}
                      </span>
                    </li>
                  ))}
                </ol>
              )}
              {can('DS-03', 'VIEW') && (
                <Link className="more" to="/dashboard/content">
                  콘텐츠 효과에서 전체 보기 →
                </Link>
              )}
            </Card>
          </div>
        </>
      )}
    </>
  )
}

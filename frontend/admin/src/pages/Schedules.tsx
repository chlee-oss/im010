import { useState } from 'react'
import { Badge, Empty, Loading, PageHead, Tabs } from '../components/ui'
import { get, post, put, qs } from '../lib/api'
import { DAYS, RUN_RESULT, TRIGGER, URL_TYPE, dateTime, today } from '../lib/format'
import { useMe } from '../lib/me'
import { useRun } from '../lib/notice'
import type { Partner, Run } from '../lib/types'
import { useLoad } from '../lib/useLoad'
import { usePartnerOptions } from '../lib/usePartnerOptions'
import { scheduleText } from './Partners'

/** BA-01 스케줄관리 [수집 일정] [실행 이력] */
export default function Schedules() {
  const [tab, setTab] = useState<'SCHEDULE' | 'RUNS'>('SCHEDULE')
  return (
    <>
      <PageHead title="스케줄관리" />
      <Tabs value={tab} onChange={setTab} tabs={[['SCHEDULE', '수집 일정'], ['RUNS', '실행 이력']]} />
      {tab === 'SCHEDULE' ? <ScheduleTab /> : <RunsTab />}
    </>
  )
}

function ScheduleTab() {
  const { can } = useMe()
  const { run, busy } = useRun()
  const { data, error, setData } = useLoad(() => get<Partner[]>('/schedules'), [])
  const [editing, setEditing] = useState<{ code: string; enabled: boolean; days: string[]; daily: boolean; time: string } | null>(null)
  const canEdit = can('BA-01', 'EDIT')
  const replace = (p: Partner) => setData((list) => list?.map((x) => (x.code === p.code ? p : x)) ?? null)

  if (!data) return <Loading error={error} />
  return (
    <>
      <p className="note">제휴사별로 하루 1회 수집합니다 (판매 종료를 1일 단위로 판단). 실패하면 30분 간격으로 2번 다시 시도합니다. [즉시 실행]은 1분 안에 배치가 시작합니다.</p>
      <table className="grid">
        <thead>
          <tr>
            <th>제휴사</th>
            <th>대상</th>
            <th>일정</th>
            <th>최근 수집</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {data.map((p) => {
            const targets = [p.postpaidUrls && '후불', p.prepaidUrls && '선불', p.monthlyUrls && '이달의'].filter(Boolean).join('·') || '–'
            const e = editing?.code === p.code ? editing : null
            return (
              <tr key={p.code}>
                <td>{p.name}</td>
                <td>{targets}</td>
                <td>
                  {e ? (
                    <div className="form-line">
                      <label className="inline">
                        <input type="checkbox" checked={e.enabled} onChange={(ev) => setEditing({ ...e, enabled: ev.target.checked })} /> 사용
                      </label>
                      <label className="inline">
                        <input type="checkbox" checked={e.daily} onChange={(ev) => setEditing({ ...e, daily: ev.target.checked })} /> 매일
                      </label>
                      {!e.daily &&
                        DAYS.map(([k, label]) => (
                          <label key={k} className="inline">
                            <input
                              type="checkbox"
                              checked={e.days.includes(k)}
                              onChange={(ev) => setEditing({ ...e, days: ev.target.checked ? [...e.days, k] : e.days.filter((d) => d !== k) })}
                            />
                            {label}
                          </label>
                        ))}
                      <input type="time" step={600} value={e.time} onChange={(ev) => setEditing({ ...e, time: ev.target.value })} />
                    </div>
                  ) : (
                    <>{p.scheduleEnabled ? scheduleText(p) : <Badge>OFF</Badge>}</>
                  )}
                </td>
                <td>
                  {p.lastRunResult ? `${dateTime(p.lastRunAt)} ${RUN_RESULT[p.lastRunResult]}` : '–'}
                  {p.jobWaiting && <Badge tone="navy">수집 대기 · 실행 중</Badge>}
                </td>
                <td className="row-actions">
                  {canEdit && e && (
                    <>
                      <button onClick={() => setEditing(null)}>취소</button>
                      <button
                        className="primary"
                        disabled={busy || (!e.daily && e.days.length === 0)}
                        onClick={() =>
                          run(
                            () => put<Partner>(`/schedules/${p.code}`, { enabled: e.enabled, days: e.daily ? 'DAILY' : DAYS.map(([k]) => k).filter((k) => e.days.includes(k)).join(','), runTime: e.time }),
                            () => '저장했습니다',
                          ).then((r) => {
                            if (r) {
                              replace(r)
                              setEditing(null)
                            }
                          })
                        }
                      >
                        저장
                      </button>
                    </>
                  )}
                  {canEdit && !e && (
                    <>
                      <button
                        onClick={() =>
                          setEditing({
                            code: p.code,
                            enabled: !!p.scheduleEnabled,
                            daily: (p.scheduleDays ?? 'DAILY') === 'DAILY',
                            days: p.scheduleDays && p.scheduleDays !== 'DAILY' ? p.scheduleDays.split(',') : [],
                            time: (p.scheduleTime ?? '04:00').slice(0, 5),
                          })
                        }
                      >
                        수정
                      </button>
                      <button disabled={busy || p.jobWaiting} onClick={() => run(() => post<Partner>(`/schedules/${p.code}/run`), () => `${p.name} 즉시 수집을 요청했습니다`).then((r) => r && replace(r))}>
                        즉시 실행
                      </button>
                    </>
                  )}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </>
  )
}

function RunsTab() {
  const partners = usePartnerOptions()
  const [from, setFrom] = useState('')
  const [to, setTo] = useState(today())
  const [partner, setPartner] = useState('')
  const [result, setResult] = useState('')
  const { data, error } = useLoad(() => get<Run[]>('/runs' + qs({ from, to, partner, result })), [from, to, partner, result])
  return (
    <>
      <div className="filter-row">
        <label>
          기간 <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /> ~ <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </label>
        <label>
          제휴사{' '}
          <select value={partner} onChange={(e) => setPartner(e.target.value)}>
            <option value="">전체</option>
            {partners.map(([c, n]) => (
              <option key={c} value={c}>
                {n}
              </option>
            ))}
          </select>
        </label>
        <label>
          결과{' '}
          <select value={result} onChange={(e) => setResult(e.target.value)}>
            <option value="">전체</option>
            {Object.entries(RUN_RESULT).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </label>
        <span className="muted">기간을 비우면 최근 7일</span>
      </div>
      {!data ? (
        <Loading error={error} />
      ) : data.length === 0 ? (
        <Empty>실행 이력이 없습니다.</Empty>
      ) : (
        <table className="grid">
          <thead>
            <tr>
              <th>실행 일시</th>
              <th>제휴사</th>
              <th>유형</th>
              <th>실행</th>
              <th>결과</th>
              <th className="num">URL</th>
              <th className="num">수집</th>
              <th className="num">신규</th>
              <th className="num">변경</th>
              <th className="num">종료</th>
              <th>메시지</th>
            </tr>
          </thead>
          <tbody>
            {data.map((r) => (
              <tr key={r.id}>
                <td>{dateTime(r.finishedAt)}</td>
                <td>{r.partnerName}</td>
                <td>{URL_TYPE[r.urlType]}</td>
                <td>
                  {TRIGGER[r.trigger]}
                  {r.attempt > 1 && ` ${r.attempt}회차`}
                </td>
                <td>{r.result === 'SUCCESS' ? '성공' : <Badge tone="red">⚠ {RUN_RESULT[r.result]}</Badge>}</td>
                <td className="num">{r.urlCount}</td>
                <td className="num">{r.collectedCount}</td>
                <td className="num">{r.newCount}</td>
                <td className="num">{r.changedCount}</td>
                <td className="num">{r.endedCount}</td>
                <td className="muted">{r.message ?? ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  )
}

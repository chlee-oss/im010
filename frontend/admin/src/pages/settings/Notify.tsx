import { useState } from 'react'
import { Badge, Empty, Loading, Modal, Pager } from '../../components/ui'
import { del, get, post, put, qs } from '../../lib/api'
import { CHANNEL_KIND, NOTIFY_STATUS, fullDateTime, today } from '../../lib/format'
import { useMe } from '../../lib/me'
import { useRun } from '../../lib/notice'
import type { AlertTypeInfo, MyNotify, NotificationRow, NotifyChannel } from '../../lib/types'
import { useLoad } from '../../lib/useLoad'

type ChannelForm = { id?: number; name: string; kind: NotifyChannel['kind']; webhookUrl: string; alertTypes: string[]; enabled: boolean }

const KIND_HELP: Record<string, string> = {
  SLACK: 'Slack: 앱 › Incoming Webhooks 에서 채널 웹훅 URL 발급 (Mattermost도 같은 형식)',
  TEAMS: 'Teams: 채널 › 워크플로 › "웹후크 요청이 수신되면 채널에 게시" 로 만든 URL',
  JANDI: '잔디: 토픽 › 커넥트 › Incoming Webhook 에서 발급한 URL',
  WEBHOOK: '그 밖의 메신저 · 중계 서버: {"type","level","title","body","sentAt"} JSON 을 POST',
}

/** [알림 채널]: 메신저 채널(웹훅)별로 받을 알림 종류 — 최고관리자만 등록 · 수정 */
export function NotifyChannels() {
  const { me, can } = useMe()
  const { run, busy } = useRun()
  const { data, error, setData } = useLoad(() => get<{ channels: NotifyChannel[]; types: AlertTypeInfo[] }>('/settings/notify/channels'), [])
  const [form, setForm] = useState<ChannelForm | null>(null)
  const [testType, setTestType] = useState('CRAWL_FAILURE')
  const editable = can('ST-02', 'EDIT') && !!me?.superAdmin

  if (!data) return <Loading error={error} />
  const label = (code: string) => data.types.find((t) => t.code === code)?.label ?? code
  const replace = (channels: NotifyChannel[] | undefined) => {
    if (channels) {
      setData({ ...data, channels })
      setForm(null)
    }
  }

  return (
    <>
      <div className="filter-row">
        <span className="muted">
          알림은 백오피스 알림함에 쌓였다가 배치가 30초 안에 채널 · 메일로 보냅니다. 실패하면 5분 · 10분 뒤 다시 보내고, 결과는 [알림 이력]에서 봅니다.
        </span>
        {editable && (
          <button className="primary" onClick={() => setForm({ name: '', kind: 'SLACK', webhookUrl: '', alertTypes: data.types.filter((t) => t.messengerDefault).map((t) => t.code), enabled: true })}>
            + 채널 추가
          </button>
        )}
      </div>
      {data.channels.length === 0 ? (
        <Empty>등록된 메신저 채널이 없습니다. 채널이 없으면 알림은 메일(내 알림 설정)로만 나갑니다.</Empty>
      ) : (
        <table className="grid">
          <thead>
            <tr>
              <th>채널</th>
              <th>메신저</th>
              <th>웹훅</th>
              <th>받는 알림</th>
              <th>사용</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {data.channels.map((c) => (
              <tr key={c.id}>
                <td>{c.name}</td>
                <td>{CHANNEL_KIND[c.kind]}</td>
                <td className="url muted">{c.webhookUrl}</td>
                <td className="wrap-cell">{c.alertTypes.map(label).join(' · ') || <span className="muted">없음</span>}</td>
                <td>{c.enabled ? '사용' : <Badge>끔</Badge>}</td>
                <td className="row-actions">
                  {editable && (
                    <>
                      <button disabled={busy} onClick={() => run(() => post<{ message: string }>(`/settings/notify/channels/${c.id}/test`), (r) => r.message)}>
                        테스트 발송
                      </button>
                      <button onClick={() => setForm({ id: c.id, name: c.name, kind: c.kind, webhookUrl: '', alertTypes: c.alertTypes, enabled: c.enabled })}>수정</button>
                      <button onClick={() => window.confirm(`${c.name} 채널을 삭제합니다.`) && run(() => del<NotifyChannel[]>(`/settings/notify/channels/${c.id}`), () => '삭제했습니다').then(replace)}>
                        삭제
                      </button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {editable && (
        <section className="panel" style={{ marginTop: 16 }}>
          <h2>알림 경로 전체 확인</h2>
          <div className="form-line">
            <select value={testType} onChange={(e) => setTestType(e.target.value)}>
              {data.types.map((t) => (
                <option key={t.code} value={t.code}>
                  {t.label}
                </option>
              ))}
            </select>
            <button disabled={busy} onClick={() => run(() => post('/settings/notify/test-alert', { alertType: testType }), () => '알림함에 넣었습니다. 30초 안에 발송되고 [알림 이력]에서 결과를 볼 수 있습니다')}>
              테스트 알림 넣기
            </button>
          </div>
          <p className="hint">고른 종류를 받는 메신저 채널과 메일 수신자에게 실제 발송 경로(알림함 → 배치 → 채널 · 메일)로 보냅니다.</p>
        </section>
      )}

      {form && (
        <Modal title={form.id ? '채널 수정' : '채널 추가'} onClose={() => setForm(null)}>
          <div className="form-grid">
            <label>
              채널 이름 <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="예: 운영팀 알림" maxLength={50} />
            </label>
            <label>
              메신저
              <select value={form.kind} onChange={(e) => setForm({ ...form, kind: e.target.value as ChannelForm['kind'] })}>
                {Object.entries(CHANNEL_KIND).map(([k, v]) => (
                  <option key={k} value={k}>
                    {v}
                  </option>
                ))}
              </select>
            </label>
            <label className="inline">
              <input type="checkbox" checked={form.enabled} onChange={(e) => setForm({ ...form, enabled: e.target.checked })} /> 사용
            </label>
            <label className="span2">
              웹훅 URL
              <input
                value={form.webhookUrl}
                onChange={(e) => setForm({ ...form, webhookUrl: e.target.value })}
                placeholder={form.id ? '바꿀 때만 입력 (비우면 기존 주소 유지)' : 'https://'}
                maxLength={1000}
                autoComplete="off"
              />
            </label>
          </div>
          <p className="hint">{KIND_HELP[form.kind]}</p>
          <fieldset className="checks">
            <legend>받는 알림</legend>
            {data.types.map((t) => (
              <label key={t.code} className="inline">
                <input
                  type="checkbox"
                  checked={form.alertTypes.includes(t.code)}
                  onChange={(e) => setForm({ ...form, alertTypes: e.target.checked ? [...form.alertTypes, t.code] : form.alertTypes.filter((x) => x !== t.code) })}
                />
                {t.label}
              </label>
            ))}
          </fieldset>
          <div className="actions">
            <button onClick={() => setForm(null)}>취소</button>
            <button
              className="primary"
              disabled={busy}
              onClick={() =>
                run(() => (form.id ? put<NotifyChannel[]>(`/settings/notify/channels/${form.id}`, form) : post<NotifyChannel[]>('/settings/notify/channels', form)), () => '저장했습니다').then(replace)
              }
            >
              저장
            </button>
          </div>
        </Modal>
      )}
    </>
  )
}

/** [알림 이력]: 알림함과 발송 결과 */
export function NotifyHistory() {
  const [from, setFrom] = useState('')
  const [to, setTo] = useState(today())
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(1)
  const types = useLoad(() => get<{ types: AlertTypeInfo[] }>('/settings/notify/channels'), [])
  const [alertType, setAlertType] = useState('')
  const { data, error } = useLoad(
    () => get<{ total: number; page: number; items: NotificationRow[] }>('/settings/notify/history' + qs({ from, to, alertType, status, page })),
    [from, to, alertType, status, page],
  )
  const label = (code: string) => types.data?.types.find((t) => t.code === code)?.label ?? code
  return (
    <>
      <div className="filter-row">
        <label>
          기간 <input type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(1) }} /> ~ <input type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(1) }} />
        </label>
        <label>
          종류{' '}
          <select value={alertType} onChange={(e) => { setAlertType(e.target.value); setPage(1) }}>
            <option value="">전체</option>
            {(types.data?.types ?? []).map((t) => (
              <option key={t.code} value={t.code}>
                {t.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          결과{' '}
          <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(1) }}>
            <option value="">전체</option>
            {Object.entries(NOTIFY_STATUS).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </label>
        <span className="muted">90일 보관</span>
      </div>
      {!data ? (
        <Loading error={error} />
      ) : data.items.length === 0 ? (
        <Empty>알림이 없습니다.</Empty>
      ) : (
        <table className="grid">
          <thead>
            <tr>
              <th>일시</th>
              <th>종류</th>
              <th>제목 · 내용</th>
              <th>결과</th>
              <th>받는 곳</th>
            </tr>
          </thead>
          <tbody>
            {data.items.map((n) => (
              <tr key={n.id}>
                <td>{fullDateTime(n.createdAt)}</td>
                <td>
                  {label(n.alertType)} {n.level === 'URGENT' && <Badge tone="red">긴급</Badge>}
                </td>
                <td className="wrap-cell">
                  <b>{n.title}</b>
                  {n.body && <div className="muted pre">{n.body}</div>}
                </td>
                <td>
                  {n.status === 'SENT' ? NOTIFY_STATUS[n.status] : <Badge tone={n.status === 'PENDING' ? 'navy' : 'amber'}>{NOTIFY_STATUS[n.status]}</Badge>}
                </td>
                <td className="muted">
                  {(n.deliveries ?? '')
                    .split('|')
                    .filter(Boolean)
                    .map((d) => {
                      const i = d.lastIndexOf(':')
                      return `${d.slice(0, i)} ${NOTIFY_STATUS[d.slice(i + 1)] ?? d.slice(i + 1)}`
                    })
                    .join(', ') || '–'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {data && <Pager page={data.page} total={data.total} onChange={setPage} />}
    </>
  )
}

/** 내 알림 설정: 메일 주소 · 메일로 받을 알림 (관리자 본인) */
export function MyNotifyForm({ onClose }: { onClose: () => void }) {
  const { run, busy } = useRun()
  const { data, error, setData } = useLoad(() => get<MyNotify>('/me/notify'), [])
  if (!data) return <Loading error={error} />
  return (
    <>
      <div className="form-grid">
        <label className="span2">
          메일 주소 <input type="email" value={data.email ?? ''} onChange={(e) => setData({ ...data, email: e.target.value })} maxLength={100} />
        </label>
      </div>
      <fieldset className="checks">
        <legend>메일로 받을 알림</legend>
        {data.types.map((t) => (
          <label key={t.code} className="inline">
            <input
              type="checkbox"
              checked={data.mailAlerts.includes(t.code)}
              onChange={(e) => setData({ ...data, mailAlerts: e.target.checked ? [...data.mailAlerts, t.code] : data.mailAlerts.filter((x) => x !== t.code) })}
            />
            {t.label}
          </label>
        ))}
      </fieldset>
      <p className="hint">메신저 알림은 채널 단위로 관리자관리 › 알림 채널에서 정합니다. 기획서 기본값: 수집 결과 요약(콘텐츠 운영자) · 기준일 경과(최고관리자)는 메일.</p>
      <div className="actions">
        <button onClick={onClose}>닫기</button>
        <button
          className="primary"
          disabled={busy}
          onClick={() => run(() => put<MyNotify>('/me/notify', { email: data.email, mailAlerts: data.mailAlerts }), () => '저장했습니다').then((r) => r && onClose())}
        >
          저장
        </button>
      </div>
    </>
  )
}

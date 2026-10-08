import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Badge, Loading, Modal, PageHead, Tabs } from '../../components/ui'
import { get, post, put } from '../../lib/api'
import { dateTime } from '../../lib/format'
import { useMe } from '../../lib/me'
import { useRun } from '../../lib/notice'
import type { AdminResult, AdminUser, Group, IpSetting } from '../../lib/types'
import { useLoad } from '../../lib/useLoad'
import { NotifyChannels, NotifyHistory } from './Notify'

type Form = { id?: number; loginId: string; name: string; dept: string; phone: string; groupId: number | ''; status: 'ACTIVE' | 'RETIRED' }

/** ST-02 관리자관리: 계정 발급 · 수정 · 퇴사, 잠금 해제 · 비밀번호 / OTP 초기화(최고관리자), 사내 IP 제한 */
/** ST-02 관리자관리 [관리자] [알림 채널] [알림 이력] */
export default function Admins() {
  const [params, setParams] = useSearchParams()
  const tab = (params.get('tab') ?? 'ADMINS') as 'ADMINS' | 'CHANNELS' | 'HISTORY'
  return (
    <>
      <PageHead title="관리자관리" />
      <Tabs value={tab} onChange={(v) => setParams(v === 'ADMINS' ? {} : { tab: v })} tabs={[['ADMINS', '관리자'], ['CHANNELS', '알림 채널'], ['HISTORY', '알림 이력']]} />
      {tab === 'CHANNELS' ? <NotifyChannels /> : tab === 'HISTORY' ? <NotifyHistory /> : <AdminList />}
    </>
  )
}

function AdminList() {
  const { me, can } = useMe()
  const { run, busy } = useRun()
  const { data, error, reload } = useLoad(() => get<AdminUser[]>('/settings/admins'), [])
  const groups = useLoad(() => (can('ST-03', 'VIEW') ? get<Group[]>('/settings/groups') : Promise.resolve<Group[]>([])), [me])
  const [form, setForm] = useState<Form | null>(null)
  const [temp, setTemp] = useState<{ loginId: string; password: string } | null>(null)
  const canEdit = can('ST-02', 'EDIT')
  const isSuper = me?.superAdmin ?? false

  const done = (r: AdminResult | undefined) => {
    if (!r) return
    if (r.tempPassword) setTemp({ loginId: r.admin.loginId, password: r.tempPassword })
    setForm(null)
    void reload()
  }
  const now = Date.now()

  if (!data) return <Loading error={error} />
  return (
    <>
      <div className="filter-row">
        <span className="muted">알림 메일 주소와 받을 알림은 각 관리자가 오른쪽 위 메뉴 › 내 알림 설정에서 정합니다.</span>
        {canEdit && (
          <button className="primary" onClick={() => setForm({ loginId: '', name: '', dept: '', phone: '', groupId: '', status: 'ACTIVE' })}>
            + 관리자 발급
          </button>
        )}
      </div>
      <table className="grid">
        <thead>
          <tr>
            <th>아이디</th>
            <th>이름</th>
            <th>소속</th>
            <th>권한 그룹</th>
            <th>상태</th>
            <th>OTP</th>
            <th>최근 로그인</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {data.map((a) => {
            const locked = a.lockedUntil && new Date(a.lockedUntil).getTime() > now
            return (
              <tr key={a.id}>
                <td>{a.loginId}</td>
                <td>{a.name}</td>
                <td>{a.dept ?? '–'}</td>
                <td>
                  {a.groupName} {a.groupSystem && <Badge tone="coral">최고</Badge>}
                </td>
                <td>
                  {a.status === 'RETIRED' ? <Badge>퇴사</Badge> : locked ? <Badge tone="red">잠김 ~{dateTime(a.lockedUntil)}</Badge> : '사용'}
                  {a.mustChangePassword && a.status === 'ACTIVE' && <Badge tone="amber">비밀번호 변경 대기</Badge>}
                </td>
                <td>{a.otpEnabled ? '등록' : <span className="muted">미등록</span>}</td>
                <td>{dateTime(a.lastLoginAt)}</td>
                <td className="row-actions">
                  {canEdit && (
                    <button onClick={() => setForm({ id: a.id, loginId: a.loginId, name: a.name, dept: a.dept ?? '', phone: a.phone ?? '', groupId: a.groupId, status: a.status })}>
                      수정
                    </button>
                  )}
                  {canEdit && isSuper && (
                    <>
                      {locked && <button onClick={() => run(() => post<AdminResult>(`/settings/admins/${a.id}/unlock`), () => '잠금을 풀었습니다').then(done)}>잠금 해제</button>}
                      <button
                        onClick={() =>
                          window.confirm(`${a.loginId} 의 비밀번호를 초기화합니다. 임시 비밀번호가 한 번만 표시됩니다.`) &&
                          run(() => post<AdminResult>(`/settings/admins/${a.id}/reset-password`), () => '비밀번호를 초기화했습니다').then(done)
                        }
                      >
                        비밀번호 초기화
                      </button>
                      {a.otpEnabled && (
                        <button
                          onClick={() =>
                            window.confirm(`${a.loginId} 의 OTP를 초기화합니다. 다음 로그인 때 OTP 앱을 다시 등록합니다.`) &&
                            run(() => post<AdminResult>(`/settings/admins/${a.id}/reset-otp`), () => 'OTP를 초기화했습니다').then(done)
                          }
                        >
                          OTP 초기화
                        </button>
                      )}
                    </>
                  )}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
      <p className="hint">잠금 해제 · 비밀번호 초기화 · OTP 초기화는 최고관리자만 할 수 있습니다. 알림 수신 설정은 사내 메신저 연동 때 추가합니다.</p>

      <IpRestriction />

      {form && (
        <Modal title={form.id ? '관리자 수정' : '관리자 발급'} onClose={() => setForm(null)}>
          <div className="form-grid">
            <label>
              아이디
              <input value={form.loginId} disabled={!!form.id} onChange={(e) => setForm({ ...form, loginId: e.target.value.toLowerCase() })} maxLength={30} />
            </label>
            <label>
              이름 <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} maxLength={50} />
            </label>
            <label>
              소속 <input value={form.dept} onChange={(e) => setForm({ ...form, dept: e.target.value })} maxLength={50} />
            </label>
            <label>
              연락처 <input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} maxLength={30} />
            </label>
            <label>
              권한 그룹
              <select value={form.groupId} onChange={(e) => setForm({ ...form, groupId: e.target.value ? Number(e.target.value) : '' })}>
                <option value="">선택</option>
                {(groups.data ?? []).map((g) => (
                  <option key={g.id} value={g.id}>
                    {g.name}
                  </option>
                ))}
              </select>
            </label>
            {form.id && (
              <label>
                상태
                <select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value as Form['status'] })}>
                  <option value="ACTIVE">사용</option>
                  <option value="RETIRED">퇴사 (로그인 불가)</option>
                </select>
              </label>
            )}
          </div>
          {!form.id && <p className="hint">발급하면 임시 비밀번호가 한 번만 표시됩니다. 첫 로그인 때 OTP 등록과 비밀번호 변경을 거칩니다.</p>}
          <div className="actions">
            <button onClick={() => setForm(null)}>취소</button>
            <button
              className="primary"
              disabled={busy}
              onClick={() =>
                run(() =>
                  form.id
                    ? put<AdminResult>(`/settings/admins/${form.id}`, { ...form, groupId: form.groupId || null })
                    : post<AdminResult>('/settings/admins', { ...form, groupId: form.groupId || null }),
                  () => '저장했습니다',
                ).then(done)
              }
            >
              {form.id ? '저장' : '발급'}
            </button>
          </div>
        </Modal>
      )}

      {temp && (
        <Modal title="임시 비밀번호" onClose={() => setTemp(null)}>
          <p>
            <b>{temp.loginId}</b> 의 임시 비밀번호입니다. 이 창을 닫으면 다시 볼 수 없으니 본인에게 안전한 방법으로 전달해 주세요.
          </p>
          <p className="temp-password">
            <code>{temp.password}</code>
          </p>
          <div className="actions">
            <button onClick={() => void navigator.clipboard?.writeText(temp.password)}>복사</button>
            <button className="primary" onClick={() => setTemp(null)}>
              확인
            </button>
          </div>
        </Modal>
      )}
    </>
  )
}

function IpRestriction() {
  const { can, me } = useMe()
  const { run, busy } = useRun()
  const { data, setData } = useLoad(() => get<IpSetting>('/settings/ip-restriction'), [])
  const [draft, setDraft] = useState<{ enabled: boolean; allowlist: string } | null>(null)
  if (!data) return null
  const v = draft ?? data
  return (
    <section className="panel" style={{ marginTop: 20 }}>
      <div className="panel-head">
        <h2>사내 IP 제한 (선택)</h2>
        {can('ST-02', 'EDIT') && me?.superAdmin && !draft && <button onClick={() => setDraft({ enabled: data.enabled, allowlist: data.allowlist })}>수정</button>}
      </div>
      <p className="hint">
        켜면 허용 목록 밖의 주소에서는 로그인부터 막힙니다. 한 줄에 IP 또는 CIDR 하나 (예: 203.0.113.0/24). 지금 접속한 주소: <code>{data.yourIp}</code> — 켤 때는 이 주소가 목록에 있어야 합니다.
        최고관리자만 바꿀 수 있습니다.
      </p>
      <div className="form-line" style={{ marginTop: 10 }}>
        <label className="inline">
          <input type="checkbox" disabled={!draft} checked={v.enabled} onChange={(e) => draft && setDraft({ ...draft, enabled: e.target.checked })} /> 사용
        </label>
      </div>
      <textarea
        className="code-area"
        rows={5}
        disabled={!draft}
        value={v.allowlist}
        onChange={(e) => draft && setDraft({ ...draft, allowlist: e.target.value })}
        placeholder={'203.0.113.0/24\n198.51.100.7'}
      />
      {draft && (
        <div className="actions">
          <button onClick={() => setDraft(null)}>취소</button>
          <button
            className="primary"
            disabled={busy}
            onClick={() =>
              run(() => put<IpSetting>('/settings/ip-restriction', draft), (r) => (r.enabled ? 'IP 제한을 켰습니다' : 'IP 제한 설정을 저장했습니다')).then((r) => {
                if (r) {
                  setData(r)
                  setDraft(null)
                }
              })
            }
          >
            저장
          </button>
        </div>
      )}
    </section>
  )
}

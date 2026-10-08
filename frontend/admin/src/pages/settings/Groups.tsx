import { useEffect, useState } from 'react'
import { Badge, Loading, Modal, PageHead } from '../../components/ui'
import { del, get, post, put } from '../../lib/api'
import { ACTION } from '../../lib/format'
import { useMe } from '../../lib/me'
import { useRun } from '../../lib/notice'
import type { Action, Group, GroupDetail } from '../../lib/types'
import { useLoad } from '../../lib/useLoad'

const COLUMNS: Action[] = ['VIEW', 'EDIT', 'DELETE', 'REVIEW', 'APPROVE', 'DOWNLOAD', 'PRIVACY']

/** ST-03 권한관리: 권한 그룹 × 프로그램 × 동작 */
export default function Groups() {
  const { can } = useMe()
  const { run, busy } = useRun()
  const groups = useLoad(() => get<Group[]>('/settings/groups'), [])
  const [selected, setSelected] = useState<number | null>(null)
  const [creating, setCreating] = useState<{ code: string; name: string } | null>(null)
  const canEdit = can('ST-03', 'EDIT')

  useEffect(() => {
    if (selected === null && groups.data?.length) setSelected(groups.data[0].id)
  }, [groups.data, selected])

  if (!groups.data) return <Loading error={groups.error} />
  return (
    <>
      <PageHead title="권한관리">
        {canEdit && (
          <button className="primary" onClick={() => setCreating({ code: '', name: '' })}>
            + 권한 그룹 추가
          </button>
        )}
      </PageHead>
      <div className="split">
        <div className="split-list">
          {groups.data.map((g) => (
            <button key={g.id} className={'list-item' + (g.id === selected ? ' on' : '')} onClick={() => setSelected(g.id)}>
              <span>
                {g.name} {g.system && <Badge tone="coral">전체</Badge>}
              </span>
              <span className="muted">{g.adminCount}명</span>
            </button>
          ))}
        </div>
        <div className="split-main">
          {selected !== null && <Matrix id={selected} canEdit={canEdit} onChanged={() => void groups.reload()} onDeleted={() => { setSelected(null); void groups.reload() }} />}
        </div>
      </div>
      {creating && (
        <Modal title="권한 그룹 추가" onClose={() => setCreating(null)}>
          <div className="form-grid">
            <label>
              코드 <input value={creating.code} onChange={(e) => setCreating({ ...creating, code: e.target.value.toUpperCase() })} placeholder="예: PARTNER_OPS" maxLength={30} />
            </label>
            <label>
              이름 <input value={creating.name} onChange={(e) => setCreating({ ...creating, name: e.target.value })} maxLength={50} />
            </label>
          </div>
          <div className="actions">
            <button onClick={() => setCreating(null)}>취소</button>
            <button
              className="primary"
              disabled={busy}
              onClick={() =>
                run(() => post<GroupDetail>('/settings/groups', creating), () => '그룹을 추가했습니다').then((d) => {
                  if (d) {
                    setCreating(null)
                    setSelected(d.group.id)
                    void groups.reload()
                  }
                })
              }
            >
              추가
            </button>
          </div>
        </Modal>
      )}
    </>
  )
}

function Matrix({ id, canEdit, onChanged, onDeleted }: { id: number; canEdit: boolean; onChanged: () => void; onDeleted: () => void }) {
  const { can } = useMe()
  const { run, busy } = useRun()
  const { data, error, setData } = useLoad(() => get<GroupDetail>(`/settings/groups/${id}`), [id])
  const [granted, setGranted] = useState<Record<string, Action[]> | null>(null)
  const [name, setName] = useState<string | null>(null)

  if (!data) return <Loading error={error} />
  const system = data.group.system
  const editable = canEdit && !system
  const value = (pid: string, original: Action[]) => granted?.[pid] ?? original
  const toggle = (pid: string, original: Action[], a: Action) => {
    const cur = value(pid, original)
    let next = cur.includes(a) ? cur.filter((x) => x !== a) : [...cur, a]
    if (a !== 'VIEW' && next.length > 0 && !next.includes('VIEW')) next = ['VIEW', ...next]
    if (a === 'VIEW' && !next.includes('VIEW')) next = []
    setGranted({ ...granted, [pid]: next })
  }

  return (
    <section className="panel">
      <div className="panel-head">
        {name === null ? (
          <h2>
            {data.group.name} <span className="muted">{data.group.code}</span>
          </h2>
        ) : (
          <div className="form-line">
            <input value={name} onChange={(e) => setName(e.target.value)} maxLength={50} />
            <button onClick={() => setName(null)}>취소</button>
            <button className="primary" disabled={busy} onClick={() => run(() => put<GroupDetail>(`/settings/groups/${id}`, { name }), () => '이름을 바꿨습니다').then((d) => { if (d) { setData(d); setName(null); onChanged() } })}>
              저장
            </button>
          </div>
        )}
        {editable && name === null && (
          <div className="actions left">
            <button onClick={() => setName(data.group.name)}>이름 변경</button>
            {can('ST-03', 'DELETE') && (
              <button
                disabled={busy || data.group.adminCount > 0}
                title={data.group.adminCount > 0 ? '속한 관리자가 있으면 삭제할 수 없습니다' : ''}
                onClick={() => window.confirm(`${data.group.name} 그룹을 삭제합니다.`) && run(() => del(`/settings/groups/${id}`), () => '삭제했습니다').then(onDeleted)}
              >
                삭제
              </button>
            )}
          </div>
        )}
      </div>
      {system && <p className="note">최고관리자 그룹은 사용 중인 모든 프로그램의 모든 동작 권한을 가지며 바꿀 수 없습니다.</p>}
      <table className="grid matrix">
        <thead>
          <tr>
            <th>프로그램</th>
            {COLUMNS.map((c) => (
              <th key={c}>{ACTION[c]}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {data.permissions.map((p) => (
            <tr key={p.programId} className={p.enabled ? '' : 'disabled'}>
              <td>
                {p.programId} {p.programName} {!p.enabled && <Badge>미사용</Badge>}
              </td>
              {COLUMNS.map((c) => (
                <td key={c} className="center">
                  {p.allowed.includes(c) ? (
                    <input type="checkbox" disabled={!editable} checked={value(p.programId, p.granted).includes(c)} onChange={() => toggle(p.programId, p.granted, c)} aria-label={`${p.programName} ${ACTION[c]}`} />
                  ) : (
                    <span className="muted">–</span>
                  )}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      {editable && (
        <div className="actions">
          <span className="muted">권한 변경은 해당 관리자의 다음 로그인부터 적용됩니다.</span>
          <button disabled={!granted} onClick={() => setGranted(null)}>
            되돌리기
          </button>
          <button
            className="primary"
            disabled={busy || !granted}
            onClick={() => {
              const body: Record<string, Action[]> = {}
              for (const p of data.permissions) body[p.programId] = value(p.programId, p.granted)
              void run(() => put<GroupDetail>(`/settings/groups/${id}/permissions`, body), () => '권한을 저장했습니다').then((d) => {
                if (d) {
                  setData(d)
                  setGranted(null)
                }
              })
            }}
          >
            저장
          </button>
        </div>
      )}
    </section>
  )
}

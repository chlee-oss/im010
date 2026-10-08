import { useState } from 'react'
import { Badge, Loading, PageHead } from '../../components/ui'
import { get, put } from '../../lib/api'
import { ACTION } from '../../lib/format'
import { useMe } from '../../lib/me'
import { useRun } from '../../lib/notice'
import type { Program } from '../../lib/types'
import { useLoad } from '../../lib/useLoad'

/** ST-01 프로그램관리: 백오피스 메뉴 · 화면 (좌측 메뉴와 권한관리의 기준) */
export default function Programs() {
  const { can, reload: reloadMe } = useMe()
  const { run, busy } = useRun()
  const { data, error, setData } = useLoad(() => get<Program[]>('/settings/programs'), [])
  const [editing, setEditing] = useState<Program | null>(null)
  const canEdit = can('ST-01', 'EDIT')

  if (!data) return <Loading error={error} />
  return (
    <>
      <PageHead title="프로그램관리" />
      <p className="note">
        화면을 추가하려면 개발이 필요하므로 여기서는 이름 · 순서 · 사용 여부만 바꿉니다. 사용을 끄면 모든 권한 그룹의 메뉴에서 숨겨집니다 (ST-01 · ST-03은 끌 수 없음).
        허용 동작은 권한관리에서 줄 수 있는 범위입니다.
      </p>
      <table className="grid">
        <thead>
          <tr>
            <th>메뉴 그룹</th>
            <th>프로그램 ID</th>
            <th>프로그램명</th>
            <th>경로</th>
            <th className="num">순서</th>
            <th>사용</th>
            <th>허용 동작</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {data.map((p) => {
            const e = editing?.id === p.id ? editing : null
            return (
              <tr key={p.id}>
                <td>{p.menuGroup}</td>
                <td>{p.id}</td>
                <td>{e ? <input value={e.name} onChange={(ev) => setEditing({ ...e, name: ev.target.value })} maxLength={50} /> : p.name}</td>
                <td className="muted">{p.path}</td>
                <td className="num">{e ? <input type="number" style={{ width: 70 }} value={e.sortOrder} onChange={(ev) => setEditing({ ...e, sortOrder: Number(ev.target.value) })} /> : p.sortOrder}</td>
                <td>
                  {e ? (
                    <input type="checkbox" checked={e.enabled} onChange={(ev) => setEditing({ ...e, enabled: ev.target.checked })} />
                  ) : p.enabled ? (
                    '●'
                  ) : (
                    <Badge>미사용</Badge>
                  )}
                </td>
                <td>{p.actions.map((a) => ACTION[a]).join(' · ')}</td>
                <td className="row-actions">
                  {canEdit && !e && <button onClick={() => setEditing(p)}>수정</button>}
                  {e && (
                    <>
                      <button onClick={() => setEditing(null)}>취소</button>
                      <button
                        className="primary"
                        disabled={busy}
                        onClick={() =>
                          run(() => put<Program[]>(`/settings/programs/${p.id}`, { name: e.name, sortOrder: e.sortOrder, enabled: e.enabled }), () => '저장했습니다').then((r) => {
                            if (r) {
                              setData(r)
                              setEditing(null)
                              void reloadMe()
                            }
                          })
                        }
                      >
                        저장
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

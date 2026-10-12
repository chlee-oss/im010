import { useState, type ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { post } from '../lib/api'
import { useMe } from '../lib/me'
import { useNotify } from '../lib/notice'
import { NewPassword } from '../pages/Login'
import { MyNotifyForm } from '../pages/settings/Notify'
import { Modal } from './ui'

/** 공통 레이아웃: 좌측 메뉴(권한 있는 프로그램만) · 상단 처리 건수 · 관리자 메뉴 */
export default function Layout({ children, onLogout }: { children: ReactNode; onLogout: () => void }) {
  const { me } = useMe()
  const navigate = useNavigate()
  const notify = useNotify()
  const [menuOpen, setMenuOpen] = useState(false)
  const [pwOpen, setPwOpen] = useState(false)
  const [notifyOpen, setNotifyOpen] = useState(false)
  const [busy, setBusy] = useState(false)

  const logout = async () => {
    await post('/auth/logout').catch(() => {})
    onLogout()
  }

  const changePassword = async (pw: string, current?: string) => {
    setBusy(true)
    try {
      await post('/auth/password', { currentPassword: current, newPassword: pw })
      notify('ok', '비밀번호를 바꿨습니다')
      setPwOpen(false)
    } catch (e) {
      notify('error', e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="shell">
      <header className="topbar">
        <NavLink to="/" className="brand">
          im<span>010</span> <small>admin</small>
        </NavLink>
        {me && (
          <div className="top-right">
            <button className="pill" onClick={() => navigate('/batch-items')}>
              점검 대기 <b>{me.badges.reviewPending}</b>
            </button>
            <button className="pill" onClick={() => navigate('/approvals')}>
              승인 대기 <b>{me.badges.approvalRequested}</b>
            </button>
            <button className="pill" onClick={() => navigate('/plans?state=SCHEDULED')}>
              오늘 게시 예약 <b>{me.badges.scheduledToday}</b>
            </button>
            <div className="user-menu">
              <button className="user" onClick={() => setMenuOpen((o) => !o)} aria-expanded={menuOpen}>
                {me.name} <span className="muted">({me.groupName})</span> ▾
              </button>
              {menuOpen && (
                <div className="user-drop" onMouseLeave={() => setMenuOpen(false)}>
                  <button
                    onClick={() => {
                      setMenuOpen(false)
                      setPwOpen(true)
                    }}
                  >
                    비밀번호 변경
                  </button>
                  <button
                    onClick={() => {
                      setMenuOpen(false)
                      setNotifyOpen(true)
                    }}
                  >
                    내 알림 설정
                  </button>
                  <button onClick={logout}>로그아웃</button>
                </div>
              )}
            </div>
          </div>
        )}
      </header>
      <nav className="sidebar" aria-label="백오피스 메뉴">
        {me?.menus.map((g) => (
          <div key={g.group} className="menu-group">
            <div className="menu-title">{g.group}</div>
            {g.items.map((i) => (
              <NavLink key={i.id} to={i.path} className="menu-item">
                <span>{i.name}</span>
                {i.id === 'BA-02' && me.badges.reviewPending > 0 && <span className="count">{me.badges.reviewPending}</span>}
                {i.id === 'BA-03' && me.badges.approvalRequested > 0 && <span className="count">{me.badges.approvalRequested}</span>}
              </NavLink>
            ))}
          </div>
        ))}
      </nav>
      <main className="content">{children}</main>
      {pwOpen && (
        <Modal title="비밀번호 변경" onClose={() => setPwOpen(false)}>
          <NewPassword withCurrent busy={busy} onSubmit={changePassword} />
        </Modal>
      )}
      {notifyOpen && (
        <Modal title="내 알림 설정" onClose={() => setNotifyOpen(false)}>
          <MyNotifyForm onClose={() => setNotifyOpen(false)} />
        </Modal>
      )}
    </div>
  )
}

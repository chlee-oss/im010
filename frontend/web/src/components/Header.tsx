import { useState } from 'react'
import { Link } from 'react-router-dom'
import { usePartners } from '../lib/partners'
import { useToast } from '../lib/toast'

// 1차: 마이페이지 · 로그인 · 선불 충전 메뉴 없음. 섹션 이동은 메인(/)의 앵커로.
const SECTIONS = [
  { href: '/#month', label: '이달의 요금제' },
  { href: '/#partners', label: '요금제', dropdown: true },
  { href: '/#prepaid', label: '선불요금제' },
  { href: '/#internet-compare', label: '인터넷' },
  { href: '/#analysis', label: '요금제분석' },
]

export default function Header() {
  const [open, setOpen] = useState(false)
  const partners = usePartners()
  const toast = useToast()
  const pending = (e: React.MouseEvent) => {
    e.preventDefault()
    toast('이벤트 페이지는 준비 중이에요')
  }

  return (
    <header>
      <div className="wrap nav-row">
        <Link className="logo" to="/">
          im<span>010</span>
        </Link>
        <nav className="main-nav" aria-label="주 메뉴">
          {SECTIONS.map((s) => (
            <div className="nav-item" key={s.href}>
              <a className="top-link" href={s.href}>
                {s.label}
                {s.dropdown ? ' ▾' : ''}
              </a>
              {s.dropdown && (
                <div className="dropdown">
                  <a href="/#partners">전체 비교</a>
                  {[...partners.values()].map((p) => (
                    <a key={p.code} href={`/#partners`} onClick={() => sessionStorage.setItem('im010.partnerTab', p.code)}>
                      {p.name}
                    </a>
                  ))}
                </div>
              )}
            </div>
          ))}
          <div className="nav-item">
            <a className="top-link" href="#" onClick={pending}>
              이벤트
            </a>
          </div>
        </nav>
        <div className="header-actions">
          <a className="btn-primary" href="/#calc">
            요금제 비교하기
          </a>
          <button
            className="menu-btn"
            type="button"
            aria-label={open ? '메뉴 닫기' : '메뉴 열기'}
            aria-expanded={open}
            aria-controls="mobileNav"
            onClick={() => setOpen(!open)}
          >
            {open ? '✕' : '☰'}
          </button>
        </div>
      </div>
      {open && (
        <nav className="mobile-nav" id="mobileNav" aria-label="모바일 메뉴" onClick={() => setOpen(false)}>
          {SECTIONS.map((s) => (
            <a key={s.href} href={s.href}>
              {s.label === '요금제' ? '요금제 전체 비교' : s.label}
            </a>
          ))}
          <a href="#" onClick={pending}>
            이벤트
          </a>
        </nav>
      )}
    </header>
  )
}

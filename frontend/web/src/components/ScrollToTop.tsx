import { useEffect, useLayoutEffect, useRef } from 'react'
import { useLocation, useNavigationType } from 'react-router-dom'

/** 방문 기록(history 항목)마다 마지막 스크롤 위치 */
const positions = new Map<string, number>()
/** 뒤로 가기 위치 복원: 목록이 API 로 늦게 채워지므로 내용 높이가 안정될 때까지(최대 3초) 위치를 붙잡아 둔다 */
const RESTORE_MAX_MS = 3000
const RESTORE_TICK_MS = 100
/** 높이가 이만큼 연속으로 그대로면 다 채워진 것으로 본다 */
const STABLE_TICKS = 4

/**
 * 화면만 바꾸는 이동(SPA)은 앞 화면의 스크롤 위치가 남는다 — 홈 아래쪽에서 요금제를 누르면 상세의 아래쪽부터 보이는 문제.
 * - 새 화면으로 이동(PUSH · REPLACE): 맨 위로
 * - 뒤로 · 앞으로 가기(POP): 그 화면에서 보던 위치로. 목록이 늦게 채워지며 브라우저의 스크롤 고정(scroll anchoring)이
 *   위치를 밀어내므로, 높이가 안정될 때까지 다시 맞춘다 (사용자가 스크롤하면 바로 멈춤)
 * - /#partners 처럼 섹션 주소가 있으면 각 화면이 그 섹션으로 옮긴다
 */
export default function ScrollToTop() {
  const { key, pathname, search, hash } = useLocation()
  const type = useNavigationType()

  // 브라우저 자동 복원은 내용이 짧을 때 0 에 멈추므로 직접 관리한다
  useEffect(() => {
    if ('scrollRestoration' in history) history.scrollRestoration = 'manual'
  }, [])

  // 지금 화면의 스크롤 위치를 기억. 어느 화면 것인지는 이동이 반영되는 순간(아래 layout effect) 바뀌는 ref 로 정한다 —
  // 이동 직후의 "맨 위로" 스크롤이 앞 화면 위치를 0 으로 덮어쓰지 않게
  const current = useRef(key)
  useEffect(() => {
    const save = () => positions.set(current.current, window.scrollY)
    // 스크롤할 때마다 + 링크를 누르는 순간에도 저장한다 (클릭 시점엔 아직 앞 화면 그대로라 정확하다)
    window.addEventListener('scroll', save, { passive: true })
    document.addEventListener('click', save, true)
    return () => {
      window.removeEventListener('scroll', save)
      document.removeEventListener('click', save, true)
    }
  }, [])

  useLayoutEffect(() => {
    current.current = key
    if (hash) return
    if (type !== 'POP') {
      // html 의 scroll-behavior: smooth 를 타지 않고 바로 맨 위로
      window.scrollTo({ top: 0, left: 0, behavior: 'instant' })
      return
    }
    const target = positions.get(key) ?? 0
    const started = Date.now()
    let timer = 0
    let stopped = false
    let lastHeight = -1
    let stable = 0
    const stop = () => {
      stopped = true
    }
    const hold = () => {
      if (stopped) return
      window.scrollTo({ top: target, left: 0, behavior: 'instant' })
      const height = document.documentElement.scrollHeight
      stable = height === lastHeight ? stable + 1 : 0
      lastHeight = height
      const settled = stable >= STABLE_TICKS && Math.abs(window.scrollY - target) <= 2
      if (!settled && Date.now() - started < RESTORE_MAX_MS) timer = window.setTimeout(hold, RESTORE_TICK_MS)
    }
    window.addEventListener('wheel', stop, { passive: true })
    window.addEventListener('touchstart', stop, { passive: true })
    window.addEventListener('keydown', stop)
    hold()
    return () => {
      window.clearTimeout(timer)
      window.removeEventListener('wheel', stop)
      window.removeEventListener('touchstart', stop)
      window.removeEventListener('keydown', stop)
    }
  }, [key, pathname, search, hash, type])

  return null
}

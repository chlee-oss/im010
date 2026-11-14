import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'

/** 목록이 API 로 늦게 채워지므로 내용 높이가 안정될 때까지(최대 3초) 위치를 붙잡아 둔다 */
const HOLD_MAX_MS = 3000
const HOLD_TICK_MS = 100
/** 높이가 이만큼 연속으로 그대로면 다 채워진 것으로 본다 */
const STABLE_TICKS = 4

/**
 * getTop() 이 돌려주는 위치로 스크롤을 붙잡아 둔다. 위쪽 목록이 늦게 채워져 페이지가 길어져도 매번 다시 맞추고,
 * 높이가 안정되면 끝낸다. 사용자가 휠 · 터치 · 키로 스크롤하면 바로 멈춘다. 돌려준 함수로 중간에 정리할 수 있다.
 * html 의 scroll-behavior: smooth 를 타지 않고 바로 이동한다.
 */
export function holdScroll(getTop: () => number | null): () => void {
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
    const target = getTop()
    const height = document.documentElement.scrollHeight
    stable = height === lastHeight ? stable + 1 : 0
    lastHeight = height
    let settled = false
    if (target != null) {
      window.scrollTo({ top: target, left: 0, behavior: 'instant' })
      // 페이지 끝 가까이라 target 까지 못 내려가는 경우도 맞춘 것으로 본다
      const reachable = Math.min(target, height - window.innerHeight)
      // API 가 느리면 응답 전에도 높이가 한동안 그대로이므로, 불러오는 중인 목록(aria-busy)이 남아 있으면 끝내지 않는다
      const busy = document.querySelector('[aria-busy="true"]') != null
      settled = !busy && stable >= STABLE_TICKS && Math.abs(window.scrollY - reachable) <= 2
    }
    if (!settled && Date.now() - started < HOLD_MAX_MS) timer = window.setTimeout(hold, HOLD_TICK_MS)
  }
  window.addEventListener('wheel', stop, { passive: true })
  window.addEventListener('touchstart', stop, { passive: true })
  window.addEventListener('keydown', stop)
  hold()
  return () => {
    stop()
    window.clearTimeout(timer)
    window.removeEventListener('wheel', stop)
    window.removeEventListener('touchstart', stop)
    window.removeEventListener('keydown', stop)
  }
}

/** 섹션 요소의 문서 기준 위치. 고정 헤더를 피하는 scroll-margin-top 을 반영한다 (scrollIntoView 와 같은 자리). */
function sectionTop(id: string): number | null {
  const el = document.getElementById(id)
  if (!el) return null
  const margin = parseFloat(getComputedStyle(el).scrollMarginTop) || 0
  return Math.max(0, el.getBoundingClientRect().top + window.scrollY - margin)
}

/**
 * /#prepaid 처럼 섹션 주소로 들어오면 그 섹션으로 옮긴다. 첫 렌더 직후에는 위쪽 목록이 비어 있어 섹션이 실제보다 위에 있으므로,
 * 목록이 채워지는 동안 섹션 위치를 다시 맞춘다. 같은 섹션 링크를 다시 눌러도(key 변경) 다시 옮긴다.
 */
export function useHashScroll() {
  const { hash, key } = useLocation()
  useEffect(() => {
    if (!hash) return
    const id = decodeURIComponent(hash.slice(1))
    return holdScroll(() => sectionTop(id))
  }, [hash, key])
}

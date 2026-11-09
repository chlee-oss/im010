import { useEffect } from 'react'

const DEFAULT_BASE = 'im010 — 알뜰폰 요금비교'

/**
 * 메인 제목 = 백오피스 검색엔진설정(ST-08)의 사이트 제목. 화면을 연 뒤 한 번만 받는다.
 * 받기 전 · 실패하면 기본 제목 (메인 HTML 은 api · 미리 렌더링이 이미 설정 제목으로 내려준다)
 */
let base = DEFAULT_BASE
let loading: Promise<string> | null = null

function loadBase(): Promise<string> {
  loading ??= fetch('/api/site-meta', { headers: { Accept: 'application/json' } })
    .then((r) => (r.ok ? r.json() : null))
    .then((m: { title?: string } | null) => {
      if (m?.title) base = m.title
      return base
    })
    .catch(() => base)
  return loading
}

/**
 * 페이지별 제목. 기본은 "제목 | im010", raw 면 받은 제목 그대로 (서비스 랜딩 "im010 - 나는 …").
 * - undefined: 메인 제목 (검색엔진설정 사이트 제목)
 * - null: 건드리지 않음 — 데이터를 받는 동안 미리 렌더링한 제목(요금제 · 글 이름)을 메인 제목으로 덮지 않게.
 *   (검색엔진이 JS 실행 화면을 데이터 도착 전에 찍으면 메인 제목으로 저장되는 문제)
 */
export function usePageTitle(title?: string | null, opts?: { raw?: boolean }) {
  const raw = opts?.raw ?? false
  useEffect(() => {
    if (title === null) return
    if (title) {
      document.title = raw ? title : `${title} | im010`
      return
    }
    // 메인: 받아 둔 값이 있으면 바로, 처음이면 받은 뒤 (그 사이 다른 화면으로 옮겼으면 덮지 않는다)
    let alive = true
    if (loading) document.title = base
    void loadBase().then((t) => {
      if (alive) document.title = t
    })
    return () => {
      alive = false
    }
  }, [title, raw])
}

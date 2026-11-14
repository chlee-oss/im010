import { useEffect } from 'react'

const DEFAULT_BASE = 'im010 — 알뜰폰 요금비교'

/** 검색엔진설정(ST-08) 페이지별 키 — 백오피스에서 제목을 바꿀 수 있는 화면 */
export type MetaPage = 'postpaid' | 'prepaid' | 'internet' | 'ai' | 'faq'

interface SiteMeta {
  title?: string
  pages?: Partial<Record<MetaPage, { title?: string; description?: string }>>
}

/**
 * 메인 제목 = 백오피스 검색엔진설정(ST-08)의 사이트 제목, 페이지별 제목 = 같은 화면의 페이지별 설정.
 * 화면을 연 뒤 한 번만 받는다. 받기 전 · 실패하면 기본 제목 (HTML 은 api · 미리 렌더링이 이미 설정 제목으로 내려준다)
 */
let base = DEFAULT_BASE
let pageTitles: SiteMeta['pages'] = {}
let loading: Promise<string> | null = null

function loadMeta(): Promise<string> {
  loading ??= fetch('/api/site-meta', { headers: { Accept: 'application/json' } })
    .then((r) => (r.ok ? r.json() : null))
    .then((m: SiteMeta | null) => {
      if (m?.title) base = m.title
      if (m?.pages) pageTitles = m.pages
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
 * - opts.page: 검색엔진설정에 그 페이지 제목이 있으면 그것을 쓴다 (없으면 title)
 */
export function usePageTitle(title?: string | null, opts?: { raw?: boolean; page?: MetaPage }) {
  const raw = opts?.raw ?? false
  const page = opts?.page
  useEffect(() => {
    if (title === null) return
    let alive = true
    if (title) {
      const custom = page ? pageTitles?.[page]?.title : undefined
      document.title = custom || (raw ? title : `${title} | im010`)
      if (page) {
        void loadMeta().then(() => {
          const t = pageTitles?.[page]?.title
          if (alive && t) document.title = t
        })
      }
      return () => {
        alive = false
      }
    }
    // 메인: 받아 둔 값이 있으면 바로, 처음이면 받은 뒤 (그 사이 다른 화면으로 옮겼으면 덮지 않는다)
    if (loading) document.title = base
    void loadMeta().then((t) => {
      if (alive) document.title = t
    })
    return () => {
      alive = false
    }
  }, [title, raw, page])
}

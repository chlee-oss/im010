// 이용 통계 (백오피스 대시보드 DS-01~03). 자체 수집 — 외부 분석 도구로 보내지 않는다.
//
// - 개인을 알아볼 수 있는 값은 보내지 않는다: 방문자 · 세션 ID는 이 브라우저에서 만든 무작위 값이다.
// - 서버(/api/events/config)가 수집을 꺼 두면 아무것도 보내지 않는다 (세션마다 한 번 확인).
// - 이벤트는 모아 두었다가 5초마다, 그리고 화면을 떠날 때 sendBeacon 으로 보낸다. 실패해도 화면 동작에는 영향이 없다.

export type PageType =
  | 'HOME'
  | 'WEEKLY'
  | 'FIND'
  | 'POSTPAID'
  | 'PREPAID'
  | 'INTERNET'
  | 'PLAN'
  | 'AI_LIST'
  | 'AI_POST'
  | 'FAQ'
  | 'TERMS'
  | 'OUT'
  | 'NOT_FOUND'
type Name = 'page_view' | 'engage' | 'click' | 'calc' | 'filter' | 'read_complete'
type Props = Record<string, string | number | boolean>

interface Ev {
  name: Name
  path: string
  pageType: PageType
  planId?: number
  postId?: number
  durationMs?: number
  props?: Props
}

const VID = 'im010.vid'
const SESSION = 'im010.session'
const ENABLED = 'im010.analytics'
const SESSION_IDLE_MS = 30 * 60 * 1000
const FLUSH_MS = 5000

interface Session {
  sid: string
  ref: 'SEARCH' | 'SOCIAL' | 'DIRECT' | 'OTHER'
  /** 검색 유입일 때 검색엔진 (naver · google · daum · bing · other) */
  engine?: string
  newVisitor: boolean
  last: number
}

let enabled: boolean | null = null
let queue: Ev[] = []
let timer: number | undefined

const store = {
  get(s: Storage, k: string) {
    try {
      return s.getItem(k)
    } catch {
      return null
    }
  },
  set(s: Storage, k: string, v: string) {
    try {
      s.setItem(k, v)
    } catch {
      /* 저장소를 쓸 수 없는 브라우저 — 이번 화면에서만 쓴다 */
    }
  },
}

const uuid = () =>
  typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0
        return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
      })

const SEARCH = /(^|\.)(google|naver|daum|bing|yahoo|zum|duckduckgo|nate)\./i
const SOCIAL = /(^|\.)(kakao|kakaocdn|facebook|instagram|t\.co|twitter|x\.com|band\.us|youtube|threads|tiktok|blog\.naver|cafe\.naver)/i

/** 세션 유입 경로 — utm_source 가 있으면 그것을, 없으면 이전 사이트 주소로 */
function refType(): Session['ref'] {
  const utm = new URLSearchParams(location.search).get('utm_source')?.toLowerCase()
  if (utm) {
    if (/naver|google|daum|bing|search/.test(utm)) return 'SEARCH'
    if (/kakao|facebook|instagram|band|youtube|social|sns/.test(utm)) return 'SOCIAL'
    return 'OTHER'
  }
  let host = ''
  try {
    host = document.referrer ? new URL(document.referrer).hostname : ''
  } catch {
    host = ''
  }
  if (!host || host === location.hostname) return 'DIRECT'
  if (/(^|\.)(blog|cafe)\.naver\.com$/i.test(host)) return 'SOCIAL'
  if (SEARCH.test(host)) return 'SEARCH'
  if (SOCIAL.test(host)) return 'SOCIAL'
  return 'OTHER'
}

/** 검색 유입의 검색엔진 — utm_source 또는 이전 사이트 주소로 */
function searchEngine(): string {
  let src = new URLSearchParams(location.search).get('utm_source')?.toLowerCase() ?? ''
  if (!src) {
    try {
      src = document.referrer ? new URL(document.referrer).hostname.toLowerCase() : ''
    } catch {
      src = ''
    }
  }
  for (const e of ['naver', 'google', 'daum', 'bing']) if (src.includes(e)) return e
  return 'other'
}

/** 30분 넘게 움직임이 없으면 새 세션 */
function session(): Session {
  const now = Date.now()
  let s: Session | null = null
  try {
    s = JSON.parse(store.get(sessionStorage, SESSION) ?? 'null')
  } catch {
    s = null
  }
  if (!s || now - s.last > SESSION_IDLE_MS) {
    let vid = store.get(localStorage, VID)
    const newVisitor = !vid
    if (!vid) {
      vid = uuid()
      store.set(localStorage, VID, vid)
    }
    const ref = refType()
    s = { sid: uuid(), ref, engine: ref === 'SEARCH' ? searchEngine() : undefined, newVisitor, last: now }
  }
  s.last = now
  store.set(sessionStorage, SESSION, JSON.stringify(s))
  return s
}

const device = (): 'PC' | 'MO' =>
  window.matchMedia?.('(max-width: 767px)').matches || /Mobi|Android|iPhone/i.test(navigator.userAgent) ? 'MO' : 'PC'

export function pageType(pathname: string): PageType {
  if (pathname === '/') return 'HOME'
  if (/^\/weekly\/?$/.test(pathname)) return 'WEEKLY'
  if (/^\/find\/?$/.test(pathname)) return 'FIND'
  if (/^\/postpaid\/?$/.test(pathname)) return 'POSTPAID'
  if (/^\/prepaid\/?$/.test(pathname)) return 'PREPAID'
  if (/^\/internet\/?$/.test(pathname)) return 'INTERNET'
  if (/^\/plans\/\d+\/?$/.test(pathname)) return 'PLAN'
  if (/^\/ai\/posts\/\d+/.test(pathname)) return 'AI_POST'
  if (pathname === '/ai' || /^\/ai\/[^/]+\/?$/.test(pathname)) return 'AI_LIST'
  if (pathname.startsWith('/faq')) return 'FAQ'
  if (pathname.startsWith('/terms/')) return 'TERMS'
  if (pathname === '/out') return 'OUT'
  return 'NOT_FOUND'
}

/** 세션마다 한 번 수집 여부를 확인한다 */
async function checkEnabled(): Promise<boolean> {
  if (enabled !== null) return enabled
  const cached = store.get(sessionStorage, ENABLED)
  if (cached !== null) {
    enabled = cached === '1'
    return enabled
  }
  try {
    const r = await fetch('/api/events/config', { headers: { Accept: 'application/json' } })
    enabled = r.ok && (await r.json()).enabled === true
  } catch {
    enabled = false
  }
  store.set(sessionStorage, ENABLED, enabled ? '1' : '0')
  return enabled
}

function flush() {
  if (timer !== undefined) {
    clearTimeout(timer)
    timer = undefined
  }
  if (!enabled || queue.length === 0) {
    if (enabled === false) queue = []
    return
  }
  const s = session()
  const body = JSON.stringify({
    vid: store.get(localStorage, VID) ?? s.sid,
    sid: s.sid,
    newVisitor: s.newVisitor,
    ref: s.ref,
    device: device(),
    events: queue.splice(0, 50),
  })
  try {
    if (!navigator.sendBeacon?.('/api/events', new Blob([body], { type: 'text/plain' }))) {
      void fetch('/api/events', { method: 'POST', body, keepalive: true, headers: { 'Content-Type': 'text/plain' } }).catch(() => {})
    }
  } catch {
    /* 보내지 못하면 버린다 */
  }
  if (queue.length > 0) schedule()
}

function schedule() {
  if (timer === undefined) timer = window.setTimeout(flush, FLUSH_MS)
}

/** 이벤트 1건 — 수집이 꺼져 있으면 버린다 */
export function track(name: Name, extra: Partial<Omit<Ev, 'name'>> = {}) {
  const path = extra.path ?? location.pathname
  queue.push({ name, path, pageType: extra.pageType ?? pageType(path), ...extra })
  if (queue.length > 200) queue = queue.slice(-200)
  // 클릭 · 이동 안내 화면은 곧 다른 사이트로 넘어갈 수 있어 바로 보낸다
  const urgent = name === 'click' || (name === 'page_view' && extra.pageType === 'OUT')
  if (enabled === null) {
    void checkEnabled().then((on) => (on ? (urgent ? flush() : schedule()) : (queue = [])))
  } else if (enabled) {
    if (urgent) flush()
    else schedule()
  } else {
    queue = []
  }
}

// ---------- 화면 이동 · 머문 시간 · 끝까지 읽음 ----------

let current: { path: string; pageType: PageType; planId?: number; postId?: number } | null = null
let visibleSince = 0
let visibleMs = 0
let readDone = false

function visibleTime() {
  return visibleMs + (document.visibilityState === 'visible' && visibleSince ? Date.now() - visibleSince : 0)
}

function endPage() {
  if (!current) return
  const ms = visibleTime()
  if (ms >= 1000) track('engage', { ...current, durationMs: ms })
  current = null
}

/** 요금제 · 글 화면의 ID (그 밖의 화면은 null) */
function idOf(type: PageType, pathname: string): string | null {
  if (type === 'PLAN') return pathname.split('/')[2] ?? null
  if (type === 'AI_POST') return pathname.split('/')[3] ?? null
  return null
}

/** 라우터가 화면을 바꿀 때마다 부른다 (같은 주소면 무시) */
export function trackPageView(pathname: string, search: string) {
  const type = pageType(pathname)
  if (current && current.path === pathname) return
  // 같은 글 · 요금제의 주소만 바뀐 경우 (예: /ai/posts/2 → /ai/posts/2/제목) 는 새 조회로 세지 않는다
  if (current && current.pageType === type && idOf(type, pathname) && idOf(type, pathname) === idOf(type, current.path)) {
    current.path = pathname
    return
  }
  endPage()
  const ids: { planId?: number; postId?: number; props?: Props } = {}
  if (type === 'PLAN') ids.planId = Number(pathname.split('/')[2])
  if (type === 'AI_POST') ids.postId = Number(pathname.split('/')[3])
  if (type === 'OUT') {
    const q = new URLSearchParams(search)
    const kind = q.get('kind') ?? ''
    const id = Number(q.get('id'))
    ids.props = { kind, id: q.get('id') ?? '' }
    if (kind === 'plan' && id > 0) ids.planId = id
  }
  // 검색으로 들어온 세션은 화면마다 검색엔진을 붙인다 (대시보드: 검색엔진별 · 검색 유입 첫 화면)
  const s = session()
  if (s.ref === 'SEARCH' && s.engine) ids.props = { ...ids.props, engine: s.engine }
  current = { path: pathname, pageType: type, planId: ids.planId, postId: ids.postId }
  visibleMs = 0
  visibleSince = document.visibilityState === 'visible' ? Date.now() : 0
  readDone = false
  track('page_view', { path: pathname, pageType: type, ...ids })
}

/** 앱 시작 시 한 번: 화면 숨김 · 떠남 · 스크롤 · 클릭 */
export function startTracking() {
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') {
      if (visibleSince) visibleMs += Date.now() - visibleSince
      visibleSince = 0
      flush()
    } else {
      visibleSince = Date.now()
    }
  })
  window.addEventListener('pagehide', () => {
    endPage()
    flush()
  })
  window.addEventListener(
    'scroll',
    () => {
      if (readDone || current?.pageType !== 'AI_POST') return
      const doc = document.documentElement
      if (window.scrollY + window.innerHeight >= doc.scrollHeight * 0.9) {
        readDone = true
        track('read_complete', { ...current })
      }
    },
    { passive: true },
  )
  document.addEventListener('click', onClick, true)
}

const SECTION_POS: Record<string, string> = {
  month: 'MONTHLY',
  partners: 'PARTNER',
  prepaid: 'PREPAID',
  calc: 'CALC',
  'internet-compare': 'INTERNET',
  internet: 'INTERNET',
  analysis: 'ANALYSIS',
}

/** 클릭한 위치: data-track 이 붙은 영역 → 메인 섹션 id → 머리말 · 꼬리말 */
function position(el: Element): string {
  const tagged = el.closest<HTMLElement>('[data-track]')
  if (tagged?.dataset.track) return tagged.dataset.track
  const section = el.closest('section[id]')
  if (section && SECTION_POS[section.id]) return SECTION_POS[section.id]
  if (el.closest('header')) return 'HEADER'
  if (el.closest('footer')) return 'FOOTER'
  return 'OTHER'
}

/** 요금제 · 글 · 메인 · 이동 안내로 가는 링크만 기록한다 */
function onClick(e: MouseEvent) {
  const a = (e.target as Element | null)?.closest?.('a[href]') as HTMLAnchorElement | null
  if (!a) return
  let url: URL
  try {
    url = new URL(a.href, location.href)
  } catch {
    return
  }
  if (url.origin !== location.origin) return
  const plan = url.pathname.match(/^\/plans\/(\d+)/)
  const post = url.pathname.match(/^\/ai\/posts\/(\d+)/)
  let target: string | null = null
  const extra: { planId?: number; postId?: number } = {}
  if (plan) {
    target = 'plan'
    extra.planId = Number(plan[1])
  } else if (post) {
    target = 'post'
    extra.postId = Number(post[1])
  } else if (url.pathname === '/out') {
    target = 'out'
  } else if (url.pathname === '/') {
    target = 'home'
  }
  if (!target) return
  // 클릭이 일어난 화면 기준으로 기록 (글 안에서 요금제로 이동 = 글의 효과)
  const here = current ?? { path: location.pathname, pageType: pageType(location.pathname) }
  track('click', {
    path: here.path,
    pageType: here.pageType,
    planId: extra.planId,
    postId: here.pageType === 'AI_POST' ? current?.postId : extra.postId,
    props: { target, pos: position(a) },
  })
}

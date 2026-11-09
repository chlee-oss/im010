#!/usr/bin/env node
// 검색 노출용 미리 렌더링 (vite build 다음에 실행 — package.json "build")
//
// dist/index.html(빌드 결과)을 틀로 삼아 주요 경로마다 <title> · description · canonical · og:* 와
// 본문 텍스트(#root 안)를 채운 정적 HTML 을 만든다. 브라우저에서는 React(createRoot)가 #root 를 새로 그리므로
// 미리 넣은 본문은 검색엔진 · 미리보기(카카오톡 · 슬랙 등)용이다.
//
// 데이터는 빌드할 때 운영 API(PRERENDER_API, 기본 https://www.im010.co.kr)에서 받는다.
// 받지 못하면 빌드를 멈춘다 — 인터넷 없이 빌드할 때만 PRERENDER_OFFLINE=1 (데이터 없는 화면만 만든다, 배포 금지).
//
// 결과 (nginx deploy/nginx/im010.conf 와 짝):
//   index.html                     메인
//   faq/ · ai/ · ai/<카테고리>/ · terms/<service|privacy>/ · out/(noindex)   각 index.html
//   plans/<id>/index.html          게시 중 요금제
//   _gone/plans/<id>/index.html    판매 종료 요금제 → nginx 가 410 으로 내려준다 (noindex)
//   ai/posts/<id>/index.html       알뜰AI 글 (주소의 slug 부분은 nginx 가 무시, canonical 은 정식 slug)
//   _shell.html                    빌드 뒤 새로 생긴 요금제 · 글에 쓰는 SPA 틀 (기본 메타)
//   404.html                       없는 경로 (noindex, 상태 코드 404)
//   robots.txt 는 public/ 그대로. sitemap.xml 은 im010-api 가 DB 기준으로 그때그때 만든다 (SitemapController)

import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const SITE = 'https://www.im010.co.kr'
const API = (process.env.PRERENDER_API || SITE).replace(/\/$/, '')
const OFFLINE = process.env.PRERENDER_OFFLINE === '1'
// 결과 폴더 · HTML 틀. 기본은 vite 빌드 결과(dist/, 틀 = dist/index.html).
// 운영 서버 매일 재생성(deploy/prerender/im010-prerender.sh)은 빈 작업 폴더 + 배포된 web/_shell.html 을 넘긴다
const DIST = process.env.PRERENDER_DIST || join(dirname(fileURLToPath(import.meta.url)), '..', 'dist')
const TEMPLATE = process.env.PRERENDER_TEMPLATE || join(DIST, 'index.html')
const OG_IMAGE = `${SITE}/og-default.png`
const SITE_NAME = 'im010'
const BASE_TITLE = 'im010 — 알뜰폰 요금비교'
// description · og:description 은 80자 이내 (네이버 서치어드바이저 권장). index.html 기본값과 같게 둔다
const DESC_MAX = 80
const BASE_DESC = '7개 제휴사 알뜰폰 후불 · 선불 요금제와 인터넷 상품을 한눈에 비교하고 최저가 요금제를 찾아보세요.'
const TERMS = { service: ['SERVICE', '이용약관'], privacy: ['PRIVACY', '개인정보처리방침'] }

// ---------- 공통 ----------

const esc = (s) =>
  String(s ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')

const won = (n) => (n == null ? '-' : Number(n).toLocaleString('ko-KR'))
const len = (s) => [...s].length
/** 한 줄로 · max 자 이내. 넘치면 가능한 한 띄어쓰기에서 자르고 … */
const oneLine = (s, max = DESC_MAX) => {
  const t = String(s ?? '').replace(/\s+/g, ' ').trim()
  if (len(t) <= max) return t
  const cut = [...t].slice(0, max - 1).join('')
  const sp = cut.lastIndexOf(' ')
  return `${(sp > max * 0.6 ? cut.slice(0, sp) : cut).replace(/[\s·,/:—-]+$/, '')}…`
}
/** 앞 조각부터 이어 붙여 max 자를 넘기 전까지만 (첫 조각이 이미 길면 oneLine 으로 자름) */
const fit = (parts, max = DESC_MAX) => {
  const list = parts.filter(Boolean)
  let out = ''
  for (const p of list) {
    if (len(out + p) > max) break
    out += p
  }
  return out ? out.trim() : oneLine(list[0] ?? '', max)
}
const ymd = new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' })
/** ISO → 2026.10.15 (한국 시간) */
const kDate = (iso) => {
  if (!iso) return ''
  const p = Object.fromEntries(ymd.formatToParts(new Date(iso)).map((x) => [x.type, x.value]))
  return `${p.year}.${p.month}.${p.day}`
}

const planPrice = (p) => (p.planType === 'POSTPAID' ? p.monthlyPrice : p.chargePrice)
const planUnit = (p) => (p.planType === 'POSTPAID' ? '원 / 월' : p.validDays ? `원 / ${p.validDays}일` : '원')
const postPath = (c) => `/ai/posts/${c.id}/${encodeURIComponent(c.slug)}`
const absUrl = (u) => (!u ? null : /^https?:\/\//.test(u) ? u : `${SITE}${u.startsWith('/') ? '' : '/'}${u}`)

async function getJson(path, { allow404 = false } = {}) {
  const res = await fetch(`${API}${path}`, { headers: { Accept: 'application/json' }, signal: AbortSignal.timeout(15000) })
  if (res.status === 204) return null
  if (res.status === 404 && allow404) return null
  if (!res.ok) throw new Error(`${res.status} ${API}${path}`)
  return res.json()
}

/** 동시에 n 개씩 */
async function mapLimit(items, n, fn) {
  const out = new Array(items.length)
  let i = 0
  await Promise.all(
    Array.from({ length: Math.min(n, items.length) }, async () => {
      while (i < items.length) {
        const k = i++
        out[k] = await fn(items[k])
      }
    }),
  )
  return out
}

// ---------- HTML 틀 ----------

const template = await readFile(TEMPLATE, 'utf8')
if (!template.includes('<div id="root"></div>')) throw new Error(`${TEMPLATE} 에 <div id="root"></div> 가 없습니다 (미리 렌더링 전 틀이어야 함)`)

/** 틀에서 페이지마다 바뀌는 메타를 지운다 (index.html 의 기본값) */
const stripped = template
  .replace(/\s*<title>[\s\S]*?<\/title>/, '')
  .replace(/\s*<meta\s+name="description"[^>]*>/g, '')
  .replace(/\s*<meta\s+name="robots"[^>]*>/g, '')
  .replace(/\s*<meta\s+property="og:(title|description|url|type|image)[^"]*"[^>]*>/g, '')
  .replace(/\s*<meta\s+name="twitter:[^"]*"[^>]*>/g, '')
  .replace(/\s*<link\s+rel="canonical"[^>]*>/g, '')

/**
 * @param {object} o
 * @param {string} o.path      canonical 경로 (null 이면 canonical 없음)
 * @param {string} o.title     <title> 전체
 * @param {string} o.description
 * @param {string} o.body      #root 안에 넣을 HTML
 * @param {boolean} [o.noindex]
 * @param {string} [o.type]    og:type
 * @param {string} [o.image]
 * @param {object[]} [o.jsonLd]
 * @param {string} [o.extraHead]
 */
function page({ path, title, description, body, noindex = false, type = 'website', image, jsonLd = [], extraHead = '' }) {
  const url = path == null ? null : `${SITE}${path}`
  const head = [
    `<title>${esc(title)}</title>`,
    `<meta name="description" content="${esc(description)}" />`,
    noindex ? '<meta name="robots" content="noindex,nofollow" />' : '',
    url && !noindex ? `<link rel="canonical" href="${esc(url)}" />` : '',
    `<meta property="og:title" content="${esc(title)}" />`,
    `<meta property="og:description" content="${esc(description)}" />`,
    url ? `<meta property="og:url" content="${esc(url)}" />` : '',
    `<meta property="og:type" content="${esc(type)}" />`,
    `<meta property="og:image" content="${esc(image || OG_IMAGE)}" />`,
    image ? '' : '<meta property="og:image:width" content="1200" />\n    <meta property="og:image:height" content="630" />',
    `<meta name="twitter:card" content="summary_large_image" />`,
    extraHead,
    ...jsonLd.map((j) => `<script type="application/ld+json">${JSON.stringify(j).replaceAll('<', '\\u003c')}</script>`),
  ]
    .filter(Boolean)
    .join('\n    ')
  return stripped
    .replace('</head>', `    ${head}\n  </head>`)
    .replace('<div id="root"></div>', `<div id="root">${body}</div>`)
}

/** React 가 그리기 전 · 검색엔진이 보는 공통 머리 · 바닥 (내부 링크) */
/**
 * 서비스별 랜딩 — 검색 결과에 서비스마다 따로 보이게 하는 독립 주소. 제목은 그대로(| im010 을 붙이지 않음).
 * 화면 쪽 문구: src/pages/ServiceLandingPage.tsx (SERVICE_PAGES) · AiListPage(/ai)
 */
const SERVICES = {
  postpaid: { path: '/postpaid', title: 'im010 - 나는 알뜰한 요금제', h1: '나는 알뜰한 요금제', label: '후불 요금제' },
  prepaid: { path: '/prepaid', title: 'im010 - 나는 선불요금제', h1: '나는 선불요금제', label: '선불요금제' },
  internet: { path: '/internet', title: 'im010 - 나는 알뜰한 인터넷', h1: '나는 알뜰한 인터넷', label: '인터넷' },
  ai: { path: '/ai', title: 'im010 - 나는 알뜰한 통신경험을 원해!', h1: '나는 알뜰한 통신경험을 원해!', label: '알뜰AI' },
}
const SERVICE_LINKS = Object.values(SERVICES)
  .map((s) => `<a href="${s.path}">${esc(s.label)}</a>`)
  .join(' · ')

function layout(main, categories = []) {
  const cats = categories.map((c) => `<a href="/ai/${esc(c.slug)}">${esc(c.name)}</a>`).join(' · ')
  return `
<header class="pre-header"><div class="wrap"><a href="/"><strong>im010</strong></a>
  <nav aria-label="주요 메뉴"><a href="/#month">이달의 요금제</a> · ${SERVICE_LINKS} · <a href="/faq">자주 묻는 질문</a></nav>
</div></header>
<main>${main}</main>
<footer class="pre-footer"><div class="wrap">
  ${cats ? `<p>알뜰AI: ${cats}</p>` : ''}
  <p>${SERVICE_LINKS}</p>
  <p><a href="/terms/service">이용약관</a> · <a href="/terms/privacy">개인정보처리방침</a> · <a href="/faq">자주 묻는 질문</a></p>
  <p>© im010 — 알뜰폰 요금비교</p>
</div></footer>
`
}

function internetItem(i) {
  return `<li>${esc(i.carrierLabel)} ${esc(i.name)} (${i.productType === 'BUNDLE' ? '인터넷 + TV 결합' : '인터넷 단독'}) — 월 <strong>${won(i.monthlyPrice)}</strong>원${i.benefits?.length ? ` · ${esc(i.benefits.join(' · '))}` : ''}</li>`
}

function planItem(p) {
  const spec = [p.dataText, `${p.networkLabel}망`, p.generation, p.qos ? `QoS ${p.qos}` : null].filter(Boolean).join(' · ')
  return `<li><a href="/plans/${p.id}">${esc(p.partnerName)} ${esc(p.name)}</a> — ${esc(spec)} · <strong>${won(planPrice(p))}</strong>${esc(planUnit(p))}</li>`
}

function cardItem(c) {
  return `<li><a href="${esc(postPath(c))}">${esc(c.title)}</a>${c.cardSummary ? ` — ${esc(c.cardSummary)}` : ''} <small>(${esc(c.categoryName)} · ${kDate(c.publishedAt)})</small></li>`
}

// ---------- 데이터 ----------

async function loadAll() {
  const [partners, postpaid, prepaid, monthly, internet, faqs, categories] = await Promise.all([
    getJson('/api/partners'),
    getJson('/api/plans?type=POSTPAID'),
    getJson('/api/plans?type=PREPAID'),
    getJson('/api/monthly-plans'),
    getJson('/api/internet-products'),
    getJson('/api/faqs'),
    getJson('/api/ai/categories'),
  ])

  // 알뜰AI 글 전부 (20개씩)
  const cards = []
  for (let pageNo = 1; ; pageNo++) {
    const pg = await getJson(`/api/ai/posts?page=${pageNo}&sort=latest`)
    if (pageNo === 1 && pg?.pinned) cards.push(pg.pinned)
    for (const c of pg?.items ?? []) if (!cards.some((x) => x.id === c.id)) cards.push(c)
    if (!pg || pg.items.length === 0 || pageNo * pg.size >= pg.total) break
  }
  const posts = (await mapLimit(cards, 4, (c) => getJson(`/api/ai/posts/${c.id}`, { allow404: true }))).filter(Boolean)

  // 요금제 상세: 게시 중 + 판매 종료(번호 1 ~ 최대+50 중 목록에 없는 것을 확인)
  const active = [...(postpaid ?? []), ...(prepaid ?? [])]
  const activeIds = new Set(active.map((p) => p.id))
  const maxId = Math.max(0, ...activeIds)
  const probeIds = Array.from({ length: maxId + 50 }, (_, i) => i + 1).filter((id) => !activeIds.has(id))
  const details = (await mapLimit([...activeIds, ...probeIds], 6, (id) => getJson(`/api/plans/${id}`, { allow404: true }))).filter(Boolean)
  const plans = details.filter((d) => activeIds.has(d.plan.id))
  const ended = details.filter((d) => !activeIds.has(d.plan.id) && d.ended)

  const terms = {}
  for (const [slug, [type]] of Object.entries(TERMS)) terms[slug] = await getJson(`/api/terms/${type}`, { allow404: true })

  return { partners, postpaid, prepaid, monthly, internet, faqs, categories, cards, posts, plans, ended, terms }
}

const EMPTY = {
  partners: [], postpaid: [], prepaid: [], monthly: [], internet: [], faqs: [], categories: [],
  cards: [], posts: [], plans: [], ended: [], terms: {},
}

let data
if (OFFLINE) {
  console.warn('[prerender] PRERENDER_OFFLINE=1 — API 없이 기본 화면만 만듭니다 (운영 배포용 빌드 아님)')
  data = EMPTY
} else {
  try {
    data = await loadAll()
  } catch (e) {
    console.error(`[prerender] ${API} 에서 데이터를 받지 못했습니다: ${e.message}`)
    console.error('[prerender] 인터넷 없이 빌드하려면 PRERENDER_OFFLINE=1 (운영 배포 금지), 다른 API 는 PRERENDER_API=<주소>')
    process.exit(1)
  }
}

// ---------- 페이지 ----------

const files = [] // [경로(dist 기준), html]
const { categories } = data

// 메인
{
  const partnerNames = data.partners.map((p) => p.name).join(' · ')
  const latest = data.cards.slice(0, 6)
  const main = `
<section class="pre-page"><div class="wrap">
  <h1>매달 통신비, 얼마나 아낄 수 있을까요?</h1>
  <p>데이터 사용량과 원하는 통신망만 알려주세요. ${esc(partnerNames || '7개 제휴사')} 알뜰폰 요금제 중 조건에 맞는 최저가 요금제를 바로 찾아 드려요.</p>
  <nav aria-label="서비스">${SERVICE_LINKS}</nav>
  ${data.monthly.length ? `<h2 id="month">이달의 요금제</h2><ul>${data.monthly.map(planItem).join('')}</ul>` : ''}
  ${data.prepaid.length ? `<h2 id="prepaid">선불 알뜰폰 요금제</h2><ul>${data.prepaid.map(planItem).join('')}</ul><p><a href="/prepaid">선불요금제 전체 보기</a></p>` : ''}
  ${data.postpaid.length ? `<h2 id="partners">후불 알뜰폰 요금제</h2><ul>${data.postpaid.map(planItem).join('')}</ul><p><a href="/postpaid">후불 요금제 전체 보기</a></p>` : ''}
  ${data.internet.length ? `<h2 id="internet-compare">인터넷 · 결합 상품</h2><ul>${data.internet.map(internetItem).join('')}</ul><p><a href="/internet">인터넷 상품 전체 보기</a></p>` : ''}
  ${latest.length ? `<h2>알뜰AI 최신 글</h2><ul>${latest.map(cardItem).join('')}</ul><p><a href="/ai">알뜰AI 전체 보기</a></p>` : ''}
</div></section>`
  files.push([
    'index.html',
    page({
      path: '/',
      title: BASE_TITLE,
      description: BASE_DESC,
      body: layout(main, categories),
      jsonLd: [{ '@context': 'https://schema.org', '@type': 'WebSite', name: SITE_NAME, alternateName: BASE_TITLE, url: `${SITE}/` }],
    }),
  ])
}

// 서비스별 랜딩 (/postpaid · /prepaid · /internet) — /ai 는 아래 알뜰AI 목록
{
  const servicePage = (key, intro, sections, description) => {
    const s = SERVICES[key]
    const main = `
<section class="pre-page"><div class="wrap">
  <nav class="breadcrumb" aria-label="현재 위치"><a href="/">홈</a> › ${esc(s.h1)}</nav>
  <h1>${esc(s.h1)}</h1>
  <p>${esc(intro)}</p>
  <nav aria-label="서비스">${SERVICE_LINKS}</nav>
  ${sections}
</div></section>`
    files.push([`${s.path.slice(1)}/index.html`, page({ path: s.path, title: s.title, description, body: layout(main, categories) })])
  }
  const n = (arr) => arr.length.toLocaleString('ko-KR')
  const postpaidPartners = new Set(data.postpaid.map((p) => p.partnerCode)).size
  const prepaidPartners = new Set(data.prepaid.map((p) => p.partnerCode)).size

  servicePage(
    'postpaid',
    '제휴사 후불 알뜰폰 요금제를 월 요금 · 데이터 · 통신망별로 비교해 보세요.',
    [
      data.monthly.length ? `<h2 id="month">이달의 요금제</h2><ul>${data.monthly.map(planItem).join('')}</ul>` : '',
      data.postpaid.length ? `<h2 id="partners">제휴사별 후불 요금제 (${n(data.postpaid)}개)</h2><ul>${data.postpaid.map(planItem).join('')}</ul>` : '<p>게시 중인 후불 요금제가 없어요.</p>',
    ].join('\n  '),
    data.postpaid.length
      ? `${postpaidPartners}개 제휴사 후불 알뜰폰 요금제 ${n(data.postpaid)}개를 월 요금 · 데이터 · 통신망별로 비교해 보세요.`
      : '제휴사 후불 알뜰폰 요금제를 월 요금 · 데이터 · 통신망별로 비교해 보세요.',
  )
  servicePage(
    'prepaid',
    '제휴사 선불 알뜰폰 요금제를 충전 금액 · 사용 기간 · 데이터별로 비교해 보세요.',
    data.prepaid.length ? `<h2 id="prepaid">선불요금제 (${n(data.prepaid)}개)</h2><ul>${data.prepaid.map(planItem).join('')}</ul>` : '<p>게시 중인 선불 요금제가 없어요.</p>',
    data.prepaid.length
      ? `${prepaidPartners}개 제휴사 선불 알뜰폰 요금제 ${n(data.prepaid)}개를 충전 금액 · 사용 기간 · 데이터별로 비교해 보세요.`
      : '제휴사 선불 알뜰폰 요금제를 충전 금액 · 사용 기간 · 데이터별로 비교해 보세요.',
  )
  const single = data.internet.filter((i) => i.productType === 'SINGLE')
  const bundle = data.internet.filter((i) => i.productType === 'BUNDLE')
  servicePage(
    'internet',
    '인터넷 단독 · 인터넷 + TV 결합 상품의 월 요금과 혜택을 통신사별로 비교해 보세요.',
    [
      single.length ? `<h2 id="internet-compare">인터넷 단독 (${n(single)}개)</h2><ul>${single.map(internetItem).join('')}</ul>` : '',
      bundle.length ? `<h2>인터넷 + TV 결합 (${n(bundle)}개)</h2><ul>${bundle.map(internetItem).join('')}</ul>` : '',
      data.internet.length ? '' : '<p>게시 중인 인터넷 상품이 없어요.</p>',
    ].join('\n  '),
    data.internet.length
      ? `인터넷 단독 · 인터넷 + TV 결합 상품 ${n(data.internet)}개의 월 요금과 혜택을 통신사별로 비교해 보세요.`
      : '인터넷 단독 · 인터넷 + TV 결합 상품의 월 요금과 혜택을 통신사별로 비교해 보세요.',
  )
}

// FAQ
{
  const main = `
<section class="doc-page"><div class="wrap narrow">
  <h1>자주 묻는 질문</h1>
  ${data.faqs.map((f) => `<details class="faq-item"><summary><span class="faq-cat">${esc(f.category)}</span> ${esc(f.question)}</summary><div class="faq-answer">${esc(f.answer)}</div></details>`).join('\n  ')}
</div></section>`
  files.push([
    'faq/index.html',
    page({
      path: '/faq',
      title: '자주 묻는 질문 | im010',
      description: '알뜰폰 개통 · 요금제 · 선불폰 이용에 대해 자주 묻는 질문과 답변을 모았어요.',
      body: layout(main, categories),
      jsonLd: data.faqs.length
        ? [{
            '@context': 'https://schema.org',
            '@type': 'FAQPage',
            mainEntity: data.faqs.map((f) => ({ '@type': 'Question', name: f.question, acceptedAnswer: { '@type': 'Answer', text: f.answer } })),
          }]
        : [],
    }),
  ])
}

// 알뜰AI 목록 (/ai, /ai/<카테고리>)
for (const cat of [null, ...categories]) {
  const list = cat ? data.cards.filter((c) => c.categorySlug === cat.slug) : data.cards
  const name = cat ? `${cat.name} · 알뜰AI` : '알뜰AI'
  const path = cat ? `/ai/${cat.slug}` : '/ai'
  const main = `
<section class="ai-page"><div class="wrap">
  <h1>${esc(cat ? name : `알뜰AI · ${SERVICES.ai.h1}`)}</h1>
  <p>알뜰폰 · 인터넷 요금과 혜택 소식을 1분 안에 읽을 수 있게 정리했어요.</p>
  <nav aria-label="카테고리"><a href="/ai">전체</a> · ${categories.map((c) => `<a href="/ai/${esc(c.slug)}">${esc(c.name)}</a>`).join(' · ')}</nav>
  ${list.length ? `<ul>${list.map(cardItem).join('')}</ul>` : '<p>아직 게시된 글이 없어요.</p>'}
</div></section>`
  files.push([
    `${path.slice(1)}/index.html`,
    page({
      path,
      // 전체 목록(/ai)은 서비스 랜딩 제목 그대로
      title: cat ? `${name} | im010` : SERVICES.ai.title,
      description: fit([
        cat ? `${cat.name} 소식을 1분 안에 읽을 수 있게 정리했어요.` : '알뜰폰 · 인터넷 요금과 혜택 소식을 1분 안에 읽을 수 있게 정리했어요.',
        list.length ? ` 최신 글: ${list[0].title}` : '',
      ]),
      body: layout(main, categories),
    }),
  ])
}

// 알뜰AI 글
for (const d of data.posts) {
  const p = d.post
  const path = postPath(p)
  const title = `${p.seoTitle || p.title} | im010`
  const description = oneLine(p.seoDescription || p.cardSummary || p.aiSummary.join(' ') || p.title)
  const image = absUrl(p.thumbnailUrl)
  const sources = p.sources.filter((s) => s.name || s.title)
  const main = `
<article class="ai-page ai-detail"><div class="wrap narrow">
  <nav class="ai-crumb" aria-label="위치"><a href="/ai">알뜰AI</a> › <a href="/ai/${esc(p.categorySlug)}">${esc(p.categoryName)}</a></nav>
  <h1>${esc(p.title)}</h1>
  <p class="ai-meta">${p.sponsored ? '<span class="ai-ad">제휴</span> ' : ''}${esc(p.authorName)} · ${kDate(p.publishedAt)}${p.contentUpdatedAt ? ` (수정 ${kDate(p.contentUpdatedAt)})` : ''} · ${p.readMinutes}분</p>
  ${p.aiSummary.length ? `<div class="ai-summary"><strong>AI 요약</strong><ul>${p.aiSummary.map((s) => `<li>${esc(s)}</li>`).join('')}</ul></div>` : ''}
  <div class="ai-body">${p.bodyHtml}</div>
  ${sources.length ? `<h2>출처</h2><ul class="ai-sources">${sources.map((s) => `<li>${esc([s.name, s.title, s.date].filter(Boolean).join(' · '))}${s.url ? ` <a href="${esc(s.url)}" rel="nofollow noopener">링크</a>` : ''}</li>`).join('')}</ul>` : ''}
  ${p.tags.length ? `<p class="ai-tags">${p.tags.map((t) => `#${esc(t)}`).join(' ')}</p>` : ''}
  ${d.relatedPlans.length ? `<h2>글에 나온 요금제</h2><ul>${d.relatedPlans.map(planItem).join('')}</ul>` : ''}
  ${d.sameCategory.length ? `<h2>${esc(p.categoryName)} 다른 글</h2><ul>${d.sameCategory.map(cardItem).join('')}</ul>` : ''}
</div></article>`
  files.push([
    `ai/posts/${p.id}/index.html`,
    page({
      path,
      title,
      description,
      body: layout(main, categories),
      type: 'article',
      image,
      extraHead: [
        `<meta property="article:published_time" content="${esc(p.publishedAt)}" />`,
        p.contentUpdatedAt ? `<meta property="article:modified_time" content="${esc(p.contentUpdatedAt)}" />` : '',
        `<meta property="article:section" content="${esc(p.categoryName)}" />`,
      ]
        .filter(Boolean)
        .join('\n    '),
      jsonLd: [{
        '@context': 'https://schema.org',
        '@type': 'Article',
        headline: p.title,
        description,
        datePublished: p.publishedAt,
        dateModified: p.contentUpdatedAt || p.publishedAt,
        author: { '@type': 'Organization', name: p.authorName || SITE_NAME },
        publisher: { '@type': 'Organization', name: SITE_NAME },
        image: image || OG_IMAGE,
        mainEntityOfPage: `${SITE}${path}`,
      }],
    }),
  ])
}

// 요금제 상세 (게시 중 · 판매 종료)
function planPage(d, ended) {
  const p = d.plan
  const prepaid = p.planType === 'PREPAID'
  // 요금제 이름이 제휴사 이름으로 시작하면 한 번만 (예: 마블링 마블링 프라임 → 마블링 프라임)
  const name = p.name.startsWith(p.partnerName) ? p.name : `${p.partnerName} ${p.name}`
  const price = `${won(planPrice(p))}${planUnit(p)}`
  const specs = [
    ['데이터', p.dataText],
    ['통신망', `${p.networkLabel}망 · ${p.generation}`],
    ['통화', d.voice],
    ['문자', d.sms],
    ['QoS', p.qos],
    [prepaid ? '충전 금액' : '월 요금', price],
    !prepaid && d.discountMonths && d.priceAfterDiscount != null ? ['할인 후', `${d.discountMonths}개월 뒤 ${won(d.priceAfterDiscount)}원 / 월`] : null,
    prepaid && p.validDays ? ['사용 기간', `${p.validDays}일`] : null,
  ].filter((s) => s && s[1])
  // 80자 이내: 이름 → 종류 → 가격 → 데이터 → 통신망 → 기준일 순으로, 넘치는 뒤쪽은 뺀다
  const shortPrice = prepaid
    ? `${won(planPrice(p))}원${p.validDays ? `/${p.validDays}일` : ''}`
    : `월 ${won(planPrice(p))}원`
  const description = fit([
    `${ended ? '[판매 종료] ' : ''}${name}`,
    ` ${prepaid ? '선불' : '후불'} 알뜰폰`,
    ` · ${shortPrice}`,
    ` · ${p.dataText.replace(/^(데이터|선불)\s*/, '데이터 ')}`,
    ` · ${p.networkLabel}망 ${p.generation}`,
    d.basisDate ? ` (${d.basisDate.slice(5).replace('-', '.')} 기준)` : '',
  ])
  const main = `
<section class="detail"><div class="wrap">
  <nav class="breadcrumb" aria-label="현재 위치"><a href="/">홈</a> › <a href="/#partners">요금제</a> › ${esc(p.partnerName)} › ${esc(p.name)}</nav>
  ${ended ? '<div class="notice ended">판매가 종료된 요금제예요. 아래 비슷한 요금제를 확인해 보세요.</div>' : ''}
  <h1 class="detail-title">${esc(name)}</h1>
  <p class="detail-meta">${esc(prepaid ? '선불' : '후불')} 알뜰폰 · ${esc(p.dataText)} · ${esc(p.networkLabel)}망 · ${esc(p.generation)}</p>
  <dl class="pre-specs">${specs.map(([k, v]) => `<dt>${esc(k)}</dt><dd>${esc(v)}</dd>`).join('')}</dl>
  ${p.tags?.length ? `<p>${p.tags.map((t) => `#${esc(t)}`).join(' ')}</p>` : ''}
  ${d.basisDate ? `<p>요금 기준일 ${esc(d.basisDate.replaceAll('-', '.'))} · 실제 요금과 조건은 제휴사 개통 페이지에서 확인해 주세요.</p>` : ''}
  ${d.similar?.length ? `<h2>비슷한 요금제</h2><ul>${d.similar.map(planItem).join('')}</ul>` : ''}
</div></section>`
  return page({
    path: `/plans/${p.id}`,
    title: `${name} | im010`,
    description,
    body: layout(main, categories),
    noindex: ended,
    type: 'product',
  })
}
for (const d of data.plans) {
  files.push([`plans/${d.plan.id}/index.html`, planPage(d, false)])
}
for (const d of data.ended) files.push([`_gone/plans/${d.plan.id}/index.html`, planPage(d, true)])

// 약관
for (const [slug, [, title]] of Object.entries(TERMS)) {
  const t = data.terms[slug]
  const main = `
<section class="doc-page"><div class="wrap narrow">
  <h1>${esc(title)}</h1>
  ${t ? `<div class="doc-meta">버전 ${esc(t.version)} · 시행일 ${esc(t.effectiveOn)}</div><div class="doc-body">${esc(t.body)}</div>` : `<p class="state-msg">아직 게시된 ${esc(title)}이 없어요.</p>`}
</div></section>`
  files.push([
    `terms/${slug}/index.html`,
    page({
      path: `/terms/${slug}`,
      title: `${title} | im010`,
      description: `im010 ${title}${t ? ` (시행일 ${t.effectiveOn})` : ''}`,
      body: layout(main, categories),
    }),
  ])
}

// 제휴사 이동 안내 (/out) — 검색 제외
files.push([
  'out/index.html',
  page({ path: null, title: '이동 중 | im010', description: '제휴사 개통 페이지로 이동하고 있어요.', body: '', noindex: true }),
])

// 없는 경로 (nginx error_page 404). SPA 가 그대로 떠서 사람에게는 404 화면이 보인다
files.push([
  '404.html',
  page({
    path: null,
    title: '페이지를 찾을 수 없음 | im010',
    description: '주소가 바뀌었거나 삭제된 페이지일 수 있어요.',
    noindex: true,
    body: layout(
      '<section class="not-found"><div class="wrap"><div class="nf-code num">404</div><h1>찾으시는 페이지가 없어요</h1><p>주소가 바뀌었거나 삭제된 페이지일 수 있어요.</p><p><a class="btn-primary" href="/">메인으로</a></p></div></section>',
      categories,
    ),
  }),
])

// 빌드 뒤 새로 생긴 요금제 · 글용 SPA 틀 (기본 메타, canonical 없음)
files.push(['_shell.html', template])


for (const [rel, html] of files) {
  const out = join(DIST, rel)
  await mkdir(dirname(out), { recursive: true })
  await writeFile(out, html, 'utf8')
}

console.log(
  `[prerender] ${API} → ${DIST}: 페이지 ${files.length - 1}개 (요금제 ${data.plans.length} · 판매 종료 ${data.ended.length} · 알뜰AI 글 ${data.posts.length})`,
)

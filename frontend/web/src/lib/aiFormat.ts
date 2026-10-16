// 알뜰AI 날짜 · 주소

const md = new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', month: '2-digit', day: '2-digit' })
const ymd = new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' })

/** 10.15 */
export function aiShortDate(iso: string | null | undefined): string {
  if (!iso) return ''
  const p = Object.fromEntries(md.formatToParts(new Date(iso)).map((x) => [x.type, x.value]))
  return `${p.month}.${p.day}`
}

/** 2026.10.15 */
export function aiDate(iso: string | null | undefined): string {
  if (!iso) return ''
  const p = Object.fromEntries(ymd.formatToParts(new Date(iso)).map((x) => [x.type, x.value]))
  return `${p.year}.${p.month}.${p.day}`
}

export const aiPostPath = (c: { id: number; slug: string }) => `/ai/posts/${c.id}/${encodeURIComponent(c.slug)}`

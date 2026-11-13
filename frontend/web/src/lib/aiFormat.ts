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

/**
 * 글 아래 "요금제 비교하기" 이동 위치 — 카테고리별로 다르게.
 * 알뜰통신: 선불 글(태그 '선불' 또는 연결 요금제가 모두 선불)은 선불요금제, 나머지는 금주의 요금을 추천해요
 * 인터넷: 인터넷 상품 비교 · 잡식잡식(그 밖): 제휴사별 요금제
 */
export function aiCompareLink(
  post: { categorySlug: string; tags: string[] },
  relatedPlans: { planType: string }[],
): { href: string; label: string } {
  if (post.categorySlug === 'mobile') {
    const prepaid =
      post.tags.includes('선불') || (relatedPlans.length > 0 && relatedPlans.every((r) => r.planType === 'PREPAID'))
    return prepaid ? { href: '/#prepaid', label: '선불요금제' } : { href: '/#month', label: '금주의 요금을 추천해요' }
  }
  if (post.categorySlug === 'internet') return { href: '/#internet-compare', label: '인터넷 상품 비교' }
  return { href: '/#partners', label: '제휴사별 요금제' }
}

export const aiPostPath =(c: { id: number; slug: string }) => `/ai/posts/${c.id}/${encodeURIComponent(c.slug)}`

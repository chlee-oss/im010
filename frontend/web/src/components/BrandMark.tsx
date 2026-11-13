/** im010 로고 표식 (시안 B-1): 서로 맞닿은 두 원 — 제휴사와 고객, 함께 · 상생. 장식이라 화면 읽기에서는 빠진다 */
export default function BrandMark({ inverse = false }: { inverse?: boolean }) {
  return (
    <svg width="34" height="22" viewBox="0 0 34 22" aria-hidden="true">
      <circle cx="11" cy="11" r="9" fill="none" stroke={inverse ? '#7FB0FF' : 'var(--primary)'} strokeWidth="3" />
      <circle cx="23" cy="11" r="9" fill="none" stroke={inverse ? '#FFFFFF' : '#7FB0FF'} strokeWidth="3" />
    </svg>
  )
}

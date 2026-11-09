import { useEffect } from 'react'

const BASE = 'im010 — 알뜰폰 요금비교'

/**
 * 페이지별 제목. 기본은 "제목 | im010", raw 면 받은 제목 그대로 (서비스 랜딩 "im010 - 나는 …").
 * - undefined: 메인 기본 제목
 * - null: 건드리지 않음 — 데이터를 받는 동안 미리 렌더링한 제목(요금제 · 글 이름)을 메인 제목으로 덮지 않게.
 *   (검색엔진이 JS 실행 화면을 데이터 도착 전에 찍으면 메인 제목으로 저장되는 문제)
 */
export function usePageTitle(title?: string | null, opts?: { raw?: boolean }) {
  const raw = opts?.raw ?? false
  useEffect(() => {
    if (title === null) return
    document.title = title ? (raw ? title : `${title} | im010`) : BASE
  }, [title, raw])
}

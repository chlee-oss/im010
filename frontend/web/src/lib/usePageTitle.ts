import { useEffect } from 'react'

const BASE = 'im010 — 알뜰폰 요금비교'

/** 페이지별 제목. (검색 노출용 메타는 배포 시 주요 페이지 미리 렌더링으로 보완 — README 참고) */
export function usePageTitle(title?: string) {
  useEffect(() => {
    document.title = title ? `${title} | im010` : BASE
  }, [title])
}

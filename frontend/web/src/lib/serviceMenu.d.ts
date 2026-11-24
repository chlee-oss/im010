export type ServiceKey = 'weekly' | 'postpaid' | 'prepaid' | 'internet'

export interface ServiceMenuItem {
  key: ServiceKey
  path: string
  label: string
}

/** 서비스 메뉴 (메인 바로가기 · 서비스 페이지 탭 · 미리 렌더링 메뉴 공용) */
export const SERVICE_MENU: readonly ServiceMenuItem[]

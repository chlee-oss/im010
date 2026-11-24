// 서비스 메뉴 — 메인 소개 아래 바로가기 · 서비스 페이지(/weekly 등) 위 탭 · 미리 렌더링 메뉴가 같은 목록을 쓴다. 타입은 serviceMenu.d.ts
// 화면(pages/HomePage.tsx · pages/ServiceLandingPage.tsx)과 미리 렌더링(scripts/prerender.mjs)이 같은 파일
// (운영 서버 매일 재생성은 deploy/scripts/setup-prerender.sh 가 /opt/im010/prerender/serviceMenu.js 로 설치).
// 이름 · 순서를 바꿀 때는 여기만 고친다. 알뜰AI 는 넣지 않는다 (헤더 · 푸터에 따로 있음)

export const SERVICE_MENU = [
  { key: 'weekly', path: '/weekly', label: '금주의 요금, 이렇게 추천해요' },
  { key: 'postpaid', path: '/postpaid', label: '조건으로 찾고, 제휴사별로 비교해요' },
  { key: 'prepaid', path: '/prepaid', label: '선불요금제를 알려 드릴게요' },
  { key: 'internet', path: '/internet', label: '인터넷도 im010에서 한 번에' },
]

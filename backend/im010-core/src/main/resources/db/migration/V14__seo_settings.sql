-- im010 환경설정 > 검색엔진설정 (ST-08): 메인 · 기본 틀의 사이트 제목 · 설명 · Open Graph 제목 · 설명
-- 값은 admin_setting. OG 제목 · 설명이 비어 있으면 사이트 제목 · 설명을 그대로 쓴다.
-- 반영: 메인(/) · 기본 틀은 im010-api 가 응답할 때 끼워 넣고(1분 캐시), 미리 렌더링(빌드 · 매일 05:00)도 같은 값을 읽는다.
-- 초기값 = 지금 고객 화면 기본값 (frontend/web/index.html)

INSERT INTO admin_setting (key, value) VALUES
    ('seo_site_title',       'im010 — 알뜰폰 요금비교'),
    ('seo_site_description', '7개 제휴사 알뜰폰 후불 · 선불 요금제와 인터넷 상품을 한눈에 비교하고 최저가 요금제를 찾아보세요.'),
    ('seo_og_title',         ''),
    ('seo_og_description',   '')
ON CONFLICT (key) DO NOTHING;

-- 처음에는 최고관리자(system 그룹, 전체 권한)만. 다른 그룹은 권한관리(ST-03)에서 준다
INSERT INTO program (id, menu_group, name, path, sort_order, enabled, actions) VALUES
    ('ST-08', '환경설정', '검색엔진설정', '/settings/seo', 67, TRUE, '{VIEW,EDIT}');

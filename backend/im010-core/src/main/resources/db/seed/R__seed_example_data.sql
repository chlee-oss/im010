-- 로컬 개발용 예시 데이터 (목업과 같은 값). local 프로필에서만 실행된다.
-- 운영 데이터는 백오피스 수집 · 승인 · 게시로 쌓인다.

INSERT INTO partner (code, name, chip_bg, chip_fg, homepage_url, sort_order) VALUES
    ('mv', '마블링',     '#FFE9E5', '#D9432F', 'https://www.marvelring.com',    1),
    ('nt', '토리모바일', '#E6F0FF', '#2158C7', 'https://ntontel.com',           2),
    ('id', '위너스텔',   '#E9F8EE', '#1E9E52', 'https://www.idowell.co.kr',     3),
    ('sg', '슈가모바일', '#FFE4EC', '#D6336C', 'https://www.sugarmobile.co.kr', 4),
    ('im', '인스모바일', '#FFF3D6', '#B3790B', 'https://www.insmobile.co.kr',   5),
    ('jt', '조이M',      '#F1E9FF', '#6C39CC', 'https://www.joytel.co.kr',      6),
    ('sw', '시월모바일', '#FDECC8', '#B26A00', 'https://siwolmobile.com',       7)
ON CONFLICT (code) DO NOTHING;

-- 수집 URL: 제휴사 사이트의 카테고리 탭마다 등록 (2026-10-08 페이지 구조 확인 결과).
-- 기본 페이지(rate_plan.do)는 첫 탭만 보여 주므로 쓰지 않는다. 대상이 제한된 탭은 아래 partner_tab 에 "수집 안 함"으로 둔다.
DELETE FROM collect_url WHERE url LIKE '%/rate_plan.do' OR url = 'https://www.joytel.co.kr/rate_plan.do?type=T001';

INSERT INTO collect_url (partner_code, url_type, url, label, sort_order) VALUES
    ('mv', 'POSTPAID', 'https://www.marvelring.com/rate_plan.do?type=T007',   '이벤트 요금제',     1),
    ('mv', 'POSTPAID', 'https://www.marvelring.com/rate_plan.do?type=T003',   'LTE 요금제',        2),
    ('mv', 'POSTPAID', 'https://www.marvelring.com/rate_plan.do?type=T002',   '5G 무제한 요금제',  3),
    ('mv', 'POSTPAID', 'https://www.marvelring.com/rate_plan.do?type=T006',   '24개월 할인 요금제', 4),
    ('nt', 'POSTPAID', 'https://ntontel.com/rate_plan.do?type=T012',          '토리모바일',        1),
    ('nt', 'PREPAID',  'https://ntontel.com/rate_plan.do?type=T004',          '토리 선불 요금제',  1),
    ('id', 'POSTPAID', 'https://www.idowell.co.kr/rate_plan.do?type=T008',    '인기요금제',        1),
    ('id', 'POSTPAID', 'https://www.idowell.co.kr/rate_plan.do?type=T009',    'U+ 이벤트요금제',   2),
    ('id', 'POSTPAID', 'https://www.idowell.co.kr/rate_plan.do?type=T006',    'U+ 후불요금제',     3),
    ('id', 'POSTPAID', 'https://www.idowell.co.kr/rate_plan.do?type=T004',    'KT 후불요금제',     4),
    ('id', 'POSTPAID', 'https://www.idowell.co.kr/rate_plan.do?type=T011',    'KT 제휴요금제',     5),
    ('id', 'PREPAID',  'https://www.idowell.co.kr/rate_plan.do?type=T002',    '선불요금제',        1),
    ('sg', 'POSTPAID', 'https://www.sugarmobile.co.kr/rate_plan.do?type=T017', '제휴',             1),
    ('sg', 'POSTPAID', 'https://www.sugarmobile.co.kr/rate_plan.do?type=T012', '6개월 할인',       2),
    ('sg', 'POSTPAID', 'https://www.sugarmobile.co.kr/rate_plan.do?type=T016', '5G 슈퍼딜',        3),
    ('sg', 'POSTPAID', 'https://www.sugarmobile.co.kr/rate_plan.do?type=T006', 'LTE',              4),
    ('sg', 'POSTPAID', 'https://www.sugarmobile.co.kr/rate_plan.do?type=T005', '5G',               5),
    ('sg', 'PREPAID',  'https://www.sugarmobile.co.kr/rate_plan.do?type=T004', '선불 요금제',      1),
    ('im', 'POSTPAID', 'https://www.insmobile.co.kr/rate_plan.do?type=T007',  '6~7개월 할인',      1),
    ('im', 'POSTPAID', 'https://www.insmobile.co.kr/rate_plan.do?type=T003',  '24개월 할인',       2),
    ('im', 'POSTPAID', 'https://www.insmobile.co.kr/rate_plan.do?type=T002',  '평생할인',          3),
    ('im', 'POSTPAID', 'https://www.insmobile.co.kr/rate_plan.do?type=T004',  '12개월 할인',       4),
    ('im', 'POSTPAID', 'https://www.insmobile.co.kr/rate_plan.do?type=T011',  '이벤트 요금제',     5),
    ('im', 'PREPAID',  'https://www.insmobile.co.kr/rate_plan.do?type=T006',  '선불 요금제',       1),
    ('jt', 'POSTPAID', 'https://www.joytel.co.kr/rate_plan.do?type=T004',     '전체 요금제',       1),
    ('sw', 'POSTPAID', 'https://siwolmobile.com/rate_plan.do?type=T006',      '이달의 요금제',     1),
    ('sw', 'POSTPAID', 'https://siwolmobile.com/rate_plan.do?type=T002',      'LTE요금제',         2),
    ('sw', 'POSTPAID', 'https://siwolmobile.com/rate_plan.do?type=T005',      '5G요금제',          3),
    ('sw', 'POSTPAID', 'https://siwolmobile.com/rate_plan.do?type=T020',      '제휴',              4),
    ('sw', 'MONTHLY',  'https://siwolmobile.com/rate_plan.do?type=T006',      '이달의 요금제',     1)
ON CONFLICT (url_type, url) DO NOTHING;

-- 수집 안 함으로 확인한 탭: 대상 제한(복지 · 임직원 · 태블릿) · 다른 탭에 모두 포함된 탭
INSERT INTO partner_tab (partner_code, url, label, status, first_seen_on, last_seen_on) VALUES
    ('nt', 'https://ntontel.com/rate_plan.do?type=T003',           '토리 복지 요금제',    'IGNORED', '2026-10-08', '2026-10-08'),
    ('nt', 'https://ntontel.com/rate_plan.do?type=T014',           '헬리오스 임직원전용', 'IGNORED', '2026-10-08', '2026-10-08'),
    ('sg', 'https://www.sugarmobile.co.kr/rate_plan.do?type=T003', '특수요금',            'IGNORED', '2026-10-08', '2026-10-08'),
    ('im', 'https://www.insmobile.co.kr/rate_plan.do?type=T005',   '태블릿 전용',         'IGNORED', '2026-10-08', '2026-10-08'),
    ('jt', 'https://www.joytel.co.kr/rate_plan.do?type=T001',      '프로모션 요금제',     'IGNORED', '2026-10-08', '2026-10-08'),
    ('sw', 'https://siwolmobile.com/rate_plan.do?type=T018',       '태블릿',              'IGNORED', '2026-10-08', '2026-10-08')
ON CONFLICT (partner_code, url) DO NOTHING;

-- 수집 스케줄: 매일 04:00 (로컬 배치는 scheduler-enabled=false, run-now 로 실행)
INSERT INTO crawl_schedule (partner_code)
SELECT code FROM partner
ON CONFLICT (partner_code) DO NOTHING;

-- 요금제 (1~12 후불, 13~15 선불)
INSERT INTO plan (id, partner_code, plan_type, status, activation_url) VALUES
    (1,  'id', 'POSTPAID', 'PUBLISHED', 'https://www.idowell.co.kr/rate_plan.do'),
    (2,  'im', 'POSTPAID', 'PUBLISHED', 'https://www.insmobile.co.kr/rate_plan.do'),
    (3,  'nt', 'POSTPAID', 'PUBLISHED', 'https://ntontel.com/rate_plan.do'),
    (4,  'jt', 'POSTPAID', 'PUBLISHED', 'https://www.joytel.co.kr/rate_plan.do?type=T001'),
    (5,  'im', 'POSTPAID', 'PUBLISHED', 'https://www.insmobile.co.kr/rate_plan.do'),
    (6,  'sg', 'POSTPAID', 'PUBLISHED', 'https://www.sugarmobile.co.kr/rate_plan.do'),
    (7,  'mv', 'POSTPAID', 'PUBLISHED', 'https://www.marvelring.com/rate_plan.do'),
    (8,  'sw', 'POSTPAID', 'PUBLISHED', 'https://siwolmobile.com/rate_plan.do'),
    (9,  'nt', 'POSTPAID', 'PUBLISHED', 'https://ntontel.com/rate_plan.do'),
    (10, 'id', 'POSTPAID', 'PUBLISHED', 'https://www.idowell.co.kr/rate_plan.do'),
    (11, 'jt', 'POSTPAID', 'PUBLISHED', 'https://www.joytel.co.kr/rate_plan.do?type=T001'),
    (12, 'mv', 'POSTPAID', 'PUBLISHED', 'https://www.marvelring.com/rate_plan.do'),
    (13, 'im', 'PREPAID',  'PUBLISHED', 'https://www.insmobile.co.kr/rate_plan.do?type=T006'),
    (14, 'id', 'PREPAID',  'PUBLISHED', 'https://www.idowell.co.kr/rate_plan.do?type=T002'),
    (15, 'sg', 'PREPAID',  'PUBLISHED', 'https://www.sugarmobile.co.kr/rate_plan.do?type=T004')
ON CONFLICT (id) DO NOTHING;

INSERT INTO plan_version (id, plan_id, version_no, name, data_text, data_gb, qos_text, voice_text, sms_text,
                          network, generation, monthly_price, charge_price, valid_days, tags, collected_on, published_at) VALUES
    (1,  1,  1, '데이터 200MB',       '데이터 200MB',          0.2,  NULL,    '기본 제공', '기본 제공', 'LGU', 'LTE', 4900,  NULL, NULL, '{}',                               '2026-10-01', now()),
    (2,  2,  1, '가벼운 3GB',         '데이터 3GB',            3,    NULL,    '기본 제공', '기본 제공', 'LGU', 'LTE', 8900,  NULL, NULL, '{LGU+망,가벼운 사용}',             '2026-10-01', now()),
    (3,  3,  1, '데이터 5GB',         '데이터 5GB',            5,    NULL,    '기본 제공', '기본 제공', 'KT',  'LTE', 9900,  NULL, NULL, '{}',                               '2026-10-01', now()),
    (4,  4,  1, '6GB + 매일 1GB',     '데이터 6GB + 매일 1GB', 6,    '1Mbps', '기본 제공', '기본 제공', 'SKT', 'LTE', 12900, NULL, NULL, '{SKT망,6개월 할인}',               '2026-10-01', now()),
    (5,  5,  1, '데이터 15GB',        '데이터 15GB',           15,   NULL,    '기본 제공', '기본 제공', 'LGU', 'LTE', 13200, NULL, NULL, '{}',                               '2026-10-01', now()),
    (6,  6,  1, '데이터 30GB',        '데이터 30GB',           30,   NULL,    '기본 제공', '기본 제공', 'KT',  '5G',  15400, NULL, NULL, '{KT망,5G}',                        '2026-10-01', now()),
    (7,  7,  1, '넉넉 11GB+',         '데이터 11GB + 매일 2GB', 11,  '1Mbps', '기본 제공', '기본 제공', 'SKT', '5G',  16500, NULL, NULL, '{SKT망,5G,유심비 무료}',           '2026-10-01', now()),
    (8,  8,  1, '데이터 50GB',        '데이터 50GB',           50,   '3Mbps', '기본 제공', '기본 제공', 'SKT', '5G',  17900, NULL, NULL, '{SKT망,첫달 무료}',                '2026-10-01', now()),
    (9,  9,  1, '데이터 100GB',       '데이터 100GB',          100,  '5Mbps', '기본 제공', '기본 제공', 'KT',  '5G',  19800, NULL, NULL, '{KT망,번호이동 혜택}',             '2026-10-01', now()),
    (10, 10, 1, '데이터 무제한',      '데이터 무제한',          NULL, NULL,    '기본 제공', '기본 제공', 'LGU', '5G',  23000, NULL, NULL, '{LGU+망,가족결합}',                '2026-10-01', now()),
    (11, 11, 1, '무제한 요금제',      '데이터 무제한',          NULL, NULL,    '기본 제공', '기본 제공', 'KT',  '5G',  24900, NULL, NULL, '{KT망,5G 완전 무제한}',            '2026-10-01', now()),
    (12, 12, 1, '데이터 무제한',      '데이터 무제한',          NULL, NULL,    '기본 제공', '기본 제공', 'SKT', '5G',  26400, NULL, NULL, '{}',                               '2026-10-01', now()),
    (13, 13, 1, '선불 1GB',           '선불 1GB · 30일',       1,    NULL,    '기본 제공', '기본 제공', 'LGU', 'LTE', NULL,  6600, 30,  '{가벼운 사용,약정 없음}',           '2026-10-01', now()),
    (14, 14, 1, '선불 3GB',           '선불 3GB · 30일',       3,    NULL,    '기본 제공', '기본 제공', 'LGU', 'LTE', NULL, 11000, 30,  '{유심 즉시 개통,약정 없음}',        '2026-10-01', now()),
    (15, 15, 1, '선불 무제한',        '선불 무제한 · 7일',     NULL, NULL,    '기본 제공', '기본 제공', 'KT',  'LTE', NULL,  9900, 7,   '{단기 여행용,약정 없음}',           '2026-10-01', now())
ON CONFLICT (id) DO NOTHING;

UPDATE plan SET published_version_id = id WHERE id BETWEEN 1 AND 15 AND published_version_id IS NULL;

-- 이달의 요금제 (제휴사 사이트 순서, 배지 없음)
INSERT INTO monthly_pick (id, partner_code, plan_id, site_order, collected_on) VALUES
    (1, 'mv', 7,  1, '2026-10-01'),
    (2, 'id', 10, 2, '2026-10-01'),
    (3, 'jt', 4,  3, '2026-10-01')
ON CONFLICT (id) DO NOTHING;

-- 인터넷 제휴업체 · 상품 (예시, 신청 URL은 예시 도메인)
INSERT INTO internet_partner (id, name, carrier, apply_url) VALUES
    (1, '○○ 인터넷 대리점', 'SKT', 'https://example.com/skt/apply'),
    (2, '△△ 통신',          'KT',  'https://example.com/kt/apply')
ON CONFLICT (id) DO NOTHING;

INSERT INTO internet_product (id, carrier, product_type, name, monthly_price, benefits, internet_partner_id, sort_order) VALUES
    (1, 'SKT', 'SINGLE', 'SKT 기가인터넷 500M',      22000, ARRAY['3년 약정 기준 월정액', '현금 사은품 최대 250,000원', '공유기 임대료 무료'], 1,    1),
    (2, 'KT',  'SINGLE', 'KT 기가인터넷 1G',         27500, ARRAY['3년 약정 기준 월정액', '현금 사은품 최대 280,000원', '인터넷+TV 결합 시 추가 할인'], 2, 2),
    (3, 'LGU', 'SINGLE', 'LG U+ 인터넷 500M',        21000, ARRAY['3년 약정 기준 월정액', '현금 사은품 최대 300,000원', '알뜰폰 결합 시 추가 할인'], NULL, 3),
    (4, 'SKT', 'BUNDLE', 'SKT 500M + B tv 이코노미', 33000, ARRAY['3년 약정 기준 월정액', '현금 사은품 최대 400,000원', '셋톱박스 임대료 포함'], 1,    4),
    (5, 'KT',  'BUNDLE', 'KT 500M + 지니TV 베이직',  35200, ARRAY['3년 약정 기준 월정액', '현금 사은품 최대 430,000원', 'OTT 부가서비스 1개월 무료'], 2, 5),
    (6, 'LGU', 'BUNDLE', 'LG U+ 500M + U+tv 베이직', 34100, ARRAY['3년 약정 기준 월정액', '현금 사은품 최대 450,000원', '알뜰폰 결합 시 추가 할인'], NULL, 6)
ON CONFLICT (id) DO NOTHING;

-- 명시적 id로 넣었으므로 시퀀스를 맞춘다
SELECT setval('plan_id_seq',             GREATEST((SELECT max(id) FROM plan), 1));
SELECT setval('plan_version_id_seq',     GREATEST((SELECT max(id) FROM plan_version), 1));
SELECT setval('monthly_pick_id_seq',     GREATEST((SELECT max(id) FROM monthly_pick), 1));
SELECT setval('internet_partner_id_seq', GREATEST((SELECT max(id) FROM internet_partner), 1));
SELECT setval('internet_product_id_seq', GREATEST((SELECT max(id) FROM internet_product), 1));

-- 알뜰AI 예시 글 (목업과 같은 제목). 썸네일이 없으면 화면에서 카테고리 기본 그림을 쓴다.
INSERT INTO ai_post (id, category_code, title, slug, card_summary, body_md, body_html, ai_summary, tags, sources_json,
                     related_plan_ids, related_internet_ids, sponsored, pinned, read_minutes, status, publish_at,
                     content_updated_at, view_count, created_by, updated_by) VALUES
    (1, 'MOBILE', '10월 알뜰폰 요금제, 무엇이 달라졌나 — 7개사 신규 요금제 한눈에', '10월-알뜰폰-요금제-무엇이-달라졌나',
     '데이터 10GB대 요금제가 1만 원 초반까지 내려왔어요. 이번 달 새로 나온 요금제와 할인 조건을 정리했습니다.',
     E'이번 달 제휴 7개사가 새 요금제를 내놨어요. 가장 큰 변화는 **데이터 10GB대 요금제 가격**입니다.\n\n## 달라진 점\n\n| 구분 | 9월 | 10월 |\n| --- | --- | --- |\n| 10GB대 최저 | 13,200원 | 11,000원 |\n| 무제한 최저 | 27,500원 | 26,400원 |\n\n## 고를 때 볼 것\n\n- 할인 기간이 끝난 뒤 요금\n- 데이터 소진 후 속도(QoS)\n',
     E'<p>이번 달 제휴 7개사가 새 요금제를 내놨어요. 가장 큰 변화는 <strong>데이터 10GB대 요금제 가격</strong>입니다.</p>\n<h2>달라진 점</h2>\n<table>\n<thead>\n<tr><th>구분</th><th>9월</th><th>10월</th></tr>\n</thead>\n<tbody>\n<tr><td>10GB대 최저</td><td>13,200원</td><td>11,000원</td></tr>\n<tr><td>무제한 최저</td><td>27,500원</td><td>26,400원</td></tr>\n</tbody>\n</table>\n<h2>고를 때 볼 것</h2>\n<ul>\n<li>할인 기간이 끝난 뒤 요금</li>\n<li>데이터 소진 후 속도(QoS)</li>\n</ul>',
     ARRAY['10GB대 요금제 최저가가 11,000원까지 내려왔어요', '할인 기간 뒤 요금을 꼭 함께 보세요', '무제한 요금제도 1,000원가량 싸졌어요'],
     ARRAY['10월요금제', '알뜰폰', '요금비교'], '[]', '{7,10,4}', '{}', FALSE, TRUE, 5, 'PUBLISHED', now() - interval '1 day', NULL, 812, 'admin', 'admin'),
    (2, 'MOBILE', 'eSIM으로 알뜰폰 개통하는 법, 10분이면 끝', 'esim으로-알뜰폰-개통하는-법',
     '유심 배송 없이 집에서 바로 개통하는 순서를 정리했어요.',
     E'유심을 택배로 받고 기다리던 시대는 지났어요. 최근 출시된 스마트폰 대부분은 eSIM을 지원해서, 알뜰폰도 집에서 바로 개통할 수 있습니다.\n\n## 1. 내 휴대폰이 eSIM을 지원하는지 확인\n\n설정 › 일반 › 정보에서 "EID" 항목이 보이면 eSIM을 쓸 수 있는 단말이에요.\n\n## 2. 본인인증 → 요금제 선택 → QR 코드 등록\n\n제휴사 개통 화면에서 본인인증을 마치면 QR 코드가 나와요. 휴대폰 카메라로 찍으면 등록이 끝납니다.\n',
     E'<p>유심을 택배로 받고 기다리던 시대는 지났어요. 최근 출시된 스마트폰 대부분은 eSIM을 지원해서, 알뜰폰도 집에서 바로 개통할 수 있습니다.</p>\n<h2>1. 내 휴대폰이 eSIM을 지원하는지 확인</h2>\n<p>설정 › 일반 › 정보에서 &quot;EID&quot; 항목이 보이면 eSIM을 쓸 수 있는 단말이에요.</p>\n<h2>2. 본인인증 → 요금제 선택 → QR 코드 등록</h2>\n<p>제휴사 개통 화면에서 본인인증을 마치면 QR 코드가 나와요. 휴대폰 카메라로 찍으면 등록이 끝납니다.</p>',
     ARRAY['eSIM 지원 단말이면 유심 배송 없이 바로 개통할 수 있어요', '본인인증 → 요금제 선택 → QR 코드 등록 순서로 진행돼요', '제휴사별 eSIM 지원 여부는 개통 화면에서 확인하세요'],
     ARRAY['eSIM', '개통', '알뜰폰'], '[{"name":"○○일보","title":"알뜰폰 eSIM 개통 쉬워진다","date":"2026.10.14","url":"https://example.com/news/esim"}]',
     '{7,10}', '{}', FALSE, FALSE, 3, 'PUBLISHED', now() - interval '1 day 2 hours', now() - interval '3 hours', 640, 'admin', 'admin'),
    (3, 'INTERNET', '인터넷 약정 끝나면 꼭 해야 할 3가지', '인터넷-약정-끝나면-꼭-해야-할-3가지',
     '약정 만료 뒤 그대로 두면 요금이 오를 수 있어요.',
     E'약정이 끝나면 할인이 사라져 요금이 오르는 경우가 많아요.\n\n## 1. 재약정 혜택 확인\n\n## 2. 다른 통신사 사은품 비교\n\n## 3. 결합 할인 다시 계산\n',
     E'<p>약정이 끝나면 할인이 사라져 요금이 오르는 경우가 많아요.</p>\n<h2>1. 재약정 혜택 확인</h2>\n<h2>2. 다른 통신사 사은품 비교</h2>\n<h2>3. 결합 할인 다시 계산</h2>',
     ARRAY['약정이 끝나면 할인이 빠져 요금이 오를 수 있어요', '재약정 혜택과 갈아타기 사은품을 함께 비교하세요', '알뜰폰 결합 할인도 다시 계산해 보세요'],
     ARRAY['인터넷', '약정', '사은품'], '[]', '{}', '{1,3}', FALSE, TRUE, 4, 'PUBLISHED', now() - interval '2 days', NULL, 455, 'admin', 'admin'),
    (4, 'MISC', '데이터 아끼는 스마트폰 설정 5가지', '데이터-아끼는-스마트폰-설정-5가지',
     '설정 몇 개만 바꿔도 데이터가 남아요.', E'자동 업데이트, 동영상 자동 재생만 꺼도 데이터가 많이 남아요.\n',
     E'<p>자동 업데이트, 동영상 자동 재생만 꺼도 데이터가 많이 남아요.</p>',
     ARRAY['앱 자동 업데이트는 와이파이에서만', '동영상 자동 재생을 끄세요', '데이터 절약 모드를 켜 두세요'],
     ARRAY['데이터', '꿀팁'], '[]', '{}', '{}', FALSE, FALSE, 2, 'PUBLISHED', now() - interval '3 days', NULL, 1203, 'admin', 'admin'),
    (5, 'MOBILE', '번호이동 vs 신규가입, 알뜰폰은 뭐가 유리할까', '번호이동-vs-신규가입',
     NULL, E'대부분은 번호이동이 유리해요. 쓰던 번호를 그대로 쓰고, 이벤트도 번호이동 기준이 많아요.\n',
     E'<p>대부분은 번호이동이 유리해요. 쓰던 번호를 그대로 쓰고, 이벤트도 번호이동 기준이 많아요.</p>',
     '{}', ARRAY['번호이동', '알뜰폰'], '[]', '{}', '{}', FALSE, FALSE, 4, 'PUBLISHED', now() - interval '5 days', NULL, 377, 'admin', 'admin'),
    (6, 'INTERNET', '인터넷 + TV 결합, 사은품만 보고 고르면 안 되는 이유', '인터넷-tv-결합-사은품',
     NULL, E'사은품이 커도 3년 동안 내는 요금이 더 많으면 손해예요.\n', E'<p>사은품이 커도 3년 동안 내는 요금이 더 많으면 손해예요.</p>',
     '{}', ARRAY['결합', '사은품'], '[]', '{}', '{4,5}', FALSE, FALSE, 5, 'PUBLISHED', now() - interval '6 days', NULL, 298, 'admin', 'admin'),
    (7, 'MOBILE', '시월모바일 10월 이벤트 요금제 총정리', '시월모바일-10월-이벤트-요금제',
     NULL, E'시월모바일이 10월 한 달 동안 이벤트 요금제를 열었어요.\n', E'<p>시월모바일이 10월 한 달 동안 이벤트 요금제를 열었어요.</p>',
     '{}', ARRAY['이벤트', '시월모바일'], '[]', '{}', '{}', TRUE, FALSE, 3, 'PUBLISHED', now() - interval '7 days', NULL, 520, 'admin', 'admin'),
    (8, 'MISC', '해외여행 갈 때 데이터, 로밍 · eSIM · 유심 비교', '해외여행-데이터-로밍-esim-유심',
     NULL, E'짧은 여행은 로밍, 일주일 이상이면 현지 eSIM이 싸요.\n', E'<p>짧은 여행은 로밍, 일주일 이상이면 현지 eSIM이 싸요.</p>',
     '{}', ARRAY['해외여행', 'eSIM'], '[]', '{}', '{}', FALSE, FALSE, 6, 'PUBLISHED', now() - interval '9 days', NULL, 233, 'admin', 'admin'),
    (9, 'INTERNET', '이사할 때 인터넷 이전 설치, 비용 아끼는 순서', '이사할-때-인터넷-이전-설치',
     NULL, E'이전 설치비는 통신사마다 달라요. 이사 2주 전에 신청하세요.\n', E'<p>이전 설치비는 통신사마다 달라요. 이사 2주 전에 신청하세요.</p>',
     '{}', ARRAY['이사', '인터넷'], '[]', '{}', '{}', FALSE, FALSE, 3, 'PUBLISHED', now() - interval '11 days', NULL, 187, 'admin', 'admin'),
    (10, 'MISC', '[작성 중] 11월 통신비 절약 체크리스트', '11월-통신비-절약-체크리스트',
     NULL, E'작성 중인 글이에요.\n', E'<p>작성 중인 글이에요.</p>',
     '{}', '{}', '[]', '{}', '{}', FALSE, FALSE, 1, 'DRAFT', NULL, NULL, 0, 'admin', 'admin')
ON CONFLICT (id) DO NOTHING;
SELECT setval('ai_post_id_seq',          GREATEST((SELECT max(id) FROM ai_post), 1));

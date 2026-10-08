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

INSERT INTO collect_url (partner_code, url_type, url) VALUES
    ('mv', 'POSTPAID', 'https://www.marvelring.com/rate_plan.do'),
    ('nt', 'POSTPAID', 'https://ntontel.com/rate_plan.do'),
    ('id', 'POSTPAID', 'https://www.idowell.co.kr/rate_plan.do'),
    ('sg', 'POSTPAID', 'https://www.sugarmobile.co.kr/rate_plan.do'),
    ('im', 'POSTPAID', 'https://www.insmobile.co.kr/rate_plan.do'),
    ('jt', 'POSTPAID', 'https://www.joytel.co.kr/rate_plan.do?type=T001'),
    ('sw', 'POSTPAID', 'https://siwolmobile.com/rate_plan.do'),
    ('im', 'PREPAID',  'https://www.insmobile.co.kr/rate_plan.do?type=T006'),
    ('id', 'PREPAID',  'https://www.idowell.co.kr/rate_plan.do?type=T002'),
    ('sg', 'PREPAID',  'https://www.sugarmobile.co.kr/rate_plan.do?type=T004')
ON CONFLICT (partner_code, url_type) DO NOTHING;

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

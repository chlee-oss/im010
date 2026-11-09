-- 대시보드 (DS-01 요약 · DS-02 관심사 · DS-03 콘텐츠): 고객 화면 이용 통계 (자체 수집)
-- 개인을 알아볼 수 있는 값은 받지 않는다: 방문자 · 세션은 브라우저가 만든 무작위 ID, IP · User-Agent 원문은 저장하지 않음.
-- 수집은 admin_setting analytics_enabled 로 켜고 끈다 (기본 꺼짐 — 개인정보 처리 검토 후 켠다).
-- 원본은 90일 보관 (batch 가 지운다), 화면은 batch 가 만든 일별 집계(stat_daily_*)만 읽는다.

CREATE TABLE analytics_event (
    id            BIGSERIAL    PRIMARY KEY,
    occurred_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    day           DATE         NOT NULL,                     -- 한국 날짜 (집계 기준)
    visitor_id    VARCHAR(36)  NOT NULL,
    session_id    VARCHAR(36)  NOT NULL,
    new_visitor   BOOLEAN      NOT NULL DEFAULT FALSE,       -- 이 브라우저의 첫 세션
    name          VARCHAR(20)  NOT NULL,                     -- page_view · engage · click · calc · filter · read_complete
    page_type     VARCHAR(20)  NOT NULL,                     -- HOME · PLAN · AI_LIST · AI_POST · FAQ · TERMS · OUT · NOT_FOUND
    path          VARCHAR(200) NOT NULL,
    ref_type      VARCHAR(10)  NOT NULL,                     -- 세션 유입: SEARCH · SOCIAL · DIRECT · OTHER
    device        VARCHAR(2)   NOT NULL CHECK (device IN ('PC', 'MO')),
    plan_id       BIGINT,
    post_id       BIGINT,
    duration_ms   INT,                                       -- engage: 화면에 머문 시간 (보이는 동안)
    props         JSONB
);
CREATE INDEX ix_analytics_event_day ON analytics_event (day);

-- 방문 (유입 · 기기). device · ref_type 의 'ALL' 은 합계 행. 방문자 = 그날의 순방문자
CREATE TABLE stat_daily_visit (
    day           DATE         NOT NULL,
    device        VARCHAR(3)   NOT NULL,
    ref_type      VARCHAR(10)  NOT NULL,
    visitors      INT          NOT NULL,
    new_visitors  INT          NOT NULL,
    sessions      INT          NOT NULL,
    page_views    INT          NOT NULL,
    PRIMARY KEY (day, device, ref_type)
);

-- 화면 종류별
CREATE TABLE stat_daily_page (
    day           DATE         NOT NULL,
    device        VARCHAR(3)   NOT NULL,
    page_type     VARCHAR(20)  NOT NULL,
    views         INT          NOT NULL,
    visitors      INT          NOT NULL,
    engage_ms     BIGINT       NOT NULL,                     -- 머문 시간 합 (engage)
    engage_count  INT          NOT NULL,
    PRIMARY KEY (day, device, page_type)
);

-- 요금제별: 상세 조회 · 카드 클릭 · 개통하기(이동 안내 화면)
CREATE TABLE stat_daily_plan (
    day           DATE         NOT NULL,
    device        VARCHAR(3)   NOT NULL,
    plan_id       BIGINT       NOT NULL,
    views         INT          NOT NULL,
    visitors      INT          NOT NULL,
    clicks        INT          NOT NULL,
    outbounds     INT          NOT NULL,
    PRIMARY KEY (day, device, plan_id)
);

-- 관심사: 항목(dim) × 값. 예: CALC_DATA/11, VIEW_NETWORK/SKT, CLICK_POS/HERO
CREATE TABLE stat_daily_interest (
    day           DATE         NOT NULL,
    device        VARCHAR(3)   NOT NULL,
    dim           VARCHAR(20)  NOT NULL,
    value         VARCHAR(50)  NOT NULL,
    cnt           INT          NOT NULL,
    PRIMARY KEY (day, device, dim, value)
);

-- 알뜰AI 글별
CREATE TABLE stat_daily_content (
    day           DATE         NOT NULL,
    device        VARCHAR(3)   NOT NULL,
    post_id       BIGINT       NOT NULL,
    views         INT          NOT NULL,
    visitors      INT          NOT NULL,
    completes     INT          NOT NULL,                     -- 끝까지 읽음 (90% 스크롤)
    engage_ms     BIGINT       NOT NULL,
    engage_count  INT          NOT NULL,
    plan_clicks   INT          NOT NULL,                     -- 글에서 요금제 · 비교 화면으로 이동
    PRIMARY KEY (day, device, post_id)
);

INSERT INTO admin_setting (key, value) VALUES
    ('analytics_enabled', 'false'),
    ('analytics_aggregated_at', '');

-- 백오피스 메뉴: 대시보드 (맨 위). 열람 범위는 권한관리(ST-03)에서 그룹별로 정한다.
-- DS-01 의 EDIT = 수집 켜기 · 끄기
INSERT INTO program (id, menu_group, name, path, sort_order, enabled, actions) VALUES
    ('DS-01', '대시보드', '요약',        '/dashboard/summary',  1, TRUE, '{VIEW,EDIT,DOWNLOAD}'),
    ('DS-02', '대시보드', '관심사',      '/dashboard/interest', 2, TRUE, '{VIEW,DOWNLOAD}'),
    ('DS-03', '대시보드', '콘텐츠 효과', '/dashboard/content',  3, TRUE, '{VIEW,DOWNLOAD}');

-- 처음 설정: 최고관리자(system)는 전체, 콘텐츠 운영자는 조회 · 다운로드
INSERT INTO group_permission (group_id, program_id, actions)
SELECT g.id, p.program_id, p.actions::TEXT[]
FROM admin_group g
JOIN (VALUES
    ('CONTENT', 'DS-01', '{VIEW,DOWNLOAD}'),
    ('CONTENT', 'DS-02', '{VIEW,DOWNLOAD}'),
    ('CONTENT', 'DS-03', '{VIEW,DOWNLOAD}')
) AS p(group_code, program_id, actions) ON p.group_code = g.code;

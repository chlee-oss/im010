-- 알뜰AI (im010_알뜰AI_기획서.md): 업계 소식 · 정보 콘텐츠 게시판
-- 카테고리(알뜰통신 · 인터넷 · 잡식잡식) · 게시글 · 일자별 조회 수, 백오피스 CT-01 · CT-02 프로그램

-- 카테고리 (CT-02): 이름 · 주소 · 카드 색 · 순서 · 노출
CREATE TABLE ai_category (
    code          VARCHAR(20)  PRIMARY KEY,
    name          VARCHAR(30)  NOT NULL,
    slug          VARCHAR(40)  NOT NULL UNIQUE,         -- /ai/{slug}
    bg_color      CHAR(7)      NOT NULL,                 -- 카드 배경 (#RRGGBB)
    accent_color  CHAR(7)      NOT NULL,                 -- 강조 · 테두리
    sort_order    INT          NOT NULL DEFAULT 0,
    exposed       BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO ai_category (code, name, slug, bg_color, accent_color, sort_order) VALUES
    ('MOBILE',   '알뜰통신', 'mobile',   '#FFF1EE', '#D9432F', 1),
    ('INTERNET', '인터넷',   'internet', '#E8F0FE', '#2158C7', 2),
    ('MISC',     '잡식잡식', 'misc',     '#FFF4D6', '#B3790B', 3);

-- 게시글 (CT-01). 고객에게 보이는 글 = status PUBLISHED 이고 publish_at 이 지난 글 (미래면 게시 예약)
CREATE TABLE ai_post (
    id                    BIGSERIAL    PRIMARY KEY,
    category_code         VARCHAR(20)  NOT NULL REFERENCES ai_category(code),
    title                 VARCHAR(120) NOT NULL,
    slug                  VARCHAR(120) NOT NULL,          -- 주소 뒤에 붙는 검색 노출용 이름 (ID가 기준)
    card_summary          VARCHAR(200),                    -- 목록 · 메인 카드 · 검색 설명
    thumbnail_url         VARCHAR(500),
    body_md               TEXT         NOT NULL DEFAULT '', -- 작성 원문 (마크다운)
    body_html             TEXT         NOT NULL DEFAULT '', -- 서버에서 변환 · 정리한 HTML (허용 태그만)
    ai_summary            TEXT[]       NOT NULL DEFAULT '{}', -- "AI 3줄 요약" (운영자 검수)
    tags                  TEXT[]       NOT NULL DEFAULT '{}',
    sources_json          TEXT         NOT NULL DEFAULT '[]', -- 출처 [{name, title, date, url}]
    related_plan_ids      BIGINT[]     NOT NULL DEFAULT '{}',
    related_internet_ids  BIGINT[]     NOT NULL DEFAULT '{}',
    sponsored             BOOLEAN      NOT NULL DEFAULT FALSE, -- 제휴 콘텐츠 표시
    pinned                BOOLEAN      NOT NULL DEFAULT FALSE, -- 카테고리 상단 고정 (카테고리당 1개)
    seo_title             VARCHAR(120),
    seo_description       VARCHAR(200),
    read_minutes          INT          NOT NULL DEFAULT 1,
    author_name           VARCHAR(50)  NOT NULL DEFAULT 'im010 편집팀',
    status                VARCHAR(10)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'HIDDEN')),
    publish_at            TIMESTAMPTZ,                     -- 게시(예약) 일시
    content_updated_at    TIMESTAMPTZ,                     -- 게시 후 본문을 고친 일시 ("수정" 표시)
    view_count            BIGINT       NOT NULL DEFAULT 0,
    created_by            VARCHAR(50),
    updated_by            VARCHAR(50),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_ai_post_list ON ai_post (status, publish_at DESC);
CREATE INDEX ix_ai_post_category ON ai_post (category_code, status, publish_at DESC);
CREATE UNIQUE INDEX ux_ai_post_pinned ON ai_post (category_code) WHERE pinned;

-- 일자별 조회 수 ("많이 본 순" — 최근 30일)
CREATE TABLE ai_post_view (
    post_id  BIGINT  NOT NULL REFERENCES ai_post(id) ON DELETE CASCADE,
    view_on  DATE    NOT NULL,
    views    INT     NOT NULL DEFAULT 0,
    PRIMARY KEY (post_id, view_on)
);

-- 백오피스 메뉴: 콘텐츠관리 > CT-01 알뜰AI 게시글관리 · CT-02 알뜰AI 카테고리관리
INSERT INTO program (id, menu_group, name, path, sort_order, enabled, actions) VALUES
    ('CT-01', '콘텐츠관리', '알뜰AI 게시글관리',   '/contents/posts',      45, TRUE, '{VIEW,EDIT,DELETE,APPROVE}'),
    ('CT-02', '콘텐츠관리', '알뜰AI 카테고리관리', '/contents/categories', 46, TRUE, '{VIEW,EDIT}');

INSERT INTO group_permission (group_id, program_id, actions)
SELECT g.id, p.program_id, p.actions::TEXT[]
FROM admin_group g
JOIN (VALUES
    ('CONTENT', 'CT-01', '{VIEW,EDIT,APPROVE}'),
    ('CONTENT', 'CT-02', '{VIEW}'),
    ('VIEWER',  'CT-01', '{VIEW}')
) AS p(group_code, program_id, actions) ON p.group_code = g.code;

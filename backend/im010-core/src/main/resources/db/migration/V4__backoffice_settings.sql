-- im010 백오피스 3-2: 환경설정 (ST-02 IP 제한 · ST-04 약관 · ST-06 Footer · ST-07 FAQ) · 인터넷 제휴업체 (PA-01 [인터넷]) · 접수 (RC-01)

-- 백오피스 설정 (ST-02 사내 IP 제한 — 결정 #24: 선택, 기본 OFF)
CREATE TABLE admin_setting (
    key         VARCHAR(50)  PRIMARY KEY,
    value       TEXT         NOT NULL,
    updated_by  VARCHAR(50),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
INSERT INTO admin_setting (key, value) VALUES
    ('ip_restriction_enabled', 'false'),
    ('ip_allowlist', '');                     -- 줄바꿈으로 구분한 IP 또는 CIDR

-- 약관 (ST-04): 게시한 버전은 고치지 않고, 새 버전을 만든다. 시행일이 지난 최신 게시 버전이 현재 약관
CREATE TABLE terms (
    id            BIGSERIAL    PRIMARY KEY,
    terms_type    VARCHAR(20)  NOT NULL CHECK (terms_type IN ('SERVICE', 'PRIVACY', 'COLLECT', 'THIRD_PARTY', 'MARKETING')),
    version       VARCHAR(20)  NOT NULL,
    body          TEXT         NOT NULL,
    effective_on  DATE         NOT NULL,
    status        VARCHAR(10)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED')),
    created_by    VARCHAR(50),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_by  VARCHAR(50),
    published_at  TIMESTAMPTZ,
    UNIQUE (terms_type, version)
);

-- Footer (ST-06): 저장할 때마다 새 버전 (변경 이력), 가장 최근 버전을 프런트에 노출
CREATE TABLE footer_version (
    id             BIGSERIAL    PRIMARY KEY,
    company_name   VARCHAR(100) NOT NULL,
    ceo            VARCHAR(50)  NOT NULL,
    business_no    VARCHAR(20)  NOT NULL,
    mail_order_no  VARCHAR(50),
    address        VARCHAR(200) NOT NULL,
    cs_phone       VARCHAR(30)  NOT NULL,
    cs_hours       VARCHAR(100),
    email          VARCHAR(100),
    notice         TEXT,                                -- 하단 고지 문구
    created_by     VARCHAR(50),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- 첫 값은 자리표시 — 오픈 전에 Footer관리에서 실제 사업자 정보로 바꾼다
INSERT INTO footer_version (company_name, ceo, business_no, mail_order_no, address, cs_phone, cs_hours, email, notice, created_by)
VALUES ('(주)아임공일공', '홍길동', '000-00-00000', '제 0000-서울-00000호', '서울특별시 OO구 OO로 00', '1600-0000',
        '평일 10:00–18:00', 'help@im010.co.kr',
        '요금 · 혜택 정보는 각 제휴사 공시 내용이며, 실제 개통 조건은 제휴사 정책에 따라 달라질 수 있습니다. 본 사이트는 알뜰폰 요금제 정보를 제공하는 통합 비교 플랫폼이며, 요금제 개통과 인터넷 신청은 각 제휴사 · 제휴업체에서 진행됩니다.',
        'system');

-- FAQ (ST-07 [FAQ])
CREATE TABLE faq (
    id          BIGSERIAL    PRIMARY KEY,
    category    VARCHAR(30)  NOT NULL,
    question    VARCHAR(200) NOT NULL,
    answer      TEXT         NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    exposed     BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_by  VARCHAR(50),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 인터넷 제휴업체 (PA-01 [인터넷]): 담당자 · 계약 기간
ALTER TABLE internet_partner
    ADD COLUMN business_no     VARCHAR(20),
    ADD COLUMN contact_name    VARCHAR(50),
    ADD COLUMN contact_phone   VARCHAR(30),
    ADD COLUMN contract_start  DATE,
    ADD COLUMN contract_end    DATE,
    ADD COLUMN memo            VARCHAR(500),
    ADD COLUMN updated_at      TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE internet_product
    ADD COLUMN updated_at  TIMESTAMPTZ NOT NULL DEFAULT now();

-- 개통신청 접수 (RC-01): 접수 당시 게시 중이던 요금제 버전
ALTER TABLE forward_log
    ADD COLUMN plan_version_id  BIGINT;
CREATE INDEX ix_forward_log_created ON forward_log (created_at);

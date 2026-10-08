-- im010 1차 스키마 (고객 화면 · 포워딩에 필요한 범위)
-- 수집 실행 · 배치 점검 · 승인 · 관리자 · 권한 테이블은 백오피스 / 배치 단계에서 추가한다.

-- 알뜰폰 제휴사
CREATE TABLE partner (
    code          VARCHAR(10)  PRIMARY KEY,           -- mv, nt, id ...
    name          VARCHAR(50)  NOT NULL,
    chip_bg       CHAR(7)      NOT NULL,              -- #RRGGBB
    chip_fg       CHAR(7)      NOT NULL,
    homepage_url  VARCHAR(500),
    exposed       BOOLEAN      NOT NULL DEFAULT TRUE,
    parser        VARCHAR(50)  NOT NULL DEFAULT 'default',
    sort_order    INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 수집 URL (PA-01): 후불 / 선불 / 이달의 요금제. URL이 없는 유형은 수집하지 않는다.
CREATE TABLE collect_url (
    id            BIGSERIAL    PRIMARY KEY,
    partner_code  VARCHAR(10)  NOT NULL REFERENCES partner(code),
    url_type      VARCHAR(10)  NOT NULL CHECK (url_type IN ('POSTPAID', 'PREPAID', 'MONTHLY')),
    url           VARCHAR(500) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (partner_code, url_type)
);
-- 같은 URL을 후불 · 선불에 동시에 등록할 수 없다 (백오피스 3.1)
CREATE UNIQUE INDEX ux_collect_url_plan_url ON collect_url (url) WHERE url_type IN ('POSTPAID', 'PREPAID');

-- 요금제 (후불 / 선불은 수집 URL 유형으로 결정, 이후 변경 불가)
CREATE TABLE plan (
    id                    BIGSERIAL    PRIMARY KEY,
    partner_code          VARCHAR(10)  NOT NULL REFERENCES partner(code),
    plan_type             VARCHAR(10)  NOT NULL CHECK (plan_type IN ('POSTPAID', 'PREPAID')),
    partner_plan_code     VARCHAR(100),                -- 제휴사 사이트의 요금제 코드 (있으면 동일 요금제 판단에 사용)
    status                VARCHAR(10)  NOT NULL DEFAULT 'PENDING'
                          CHECK (status IN ('PENDING', 'SCHEDULED', 'PUBLISHED', 'ENDED', 'HIDDEN')),
    published_version_id  BIGINT,                      -- 게시 중 버전 (아래 FK)
    activation_url        VARCHAR(500),                -- 개통하기 포워딩 대상
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_plan_status_type ON plan (status, plan_type);

-- 요금제 버전 (수집값 + 보완값). 게시 중 버전은 plan.published_version_id
CREATE TABLE plan_version (
    id                    BIGSERIAL    PRIMARY KEY,
    plan_id               BIGINT       NOT NULL REFERENCES plan(id),
    version_no            INT          NOT NULL,
    name                  VARCHAR(100) NOT NULL,       -- 요금제명 (예: 넉넉 11GB+)
    data_text             VARCHAR(100) NOT NULL,       -- 화면 표기 (예: 데이터 11GB + 매일 2GB)
    data_gb               NUMERIC(8,2),                -- 기본 제공량 (계산기 비교용), NULL = 무제한
    qos_text              VARCHAR(50),                 -- 소진 후 속도 (예: 1Mbps)
    voice_text            VARCHAR(50),
    sms_text              VARCHAR(50),
    network               VARCHAR(5)   NOT NULL CHECK (network IN ('SKT', 'KT', 'LGU')),
    generation            VARCHAR(5)   NOT NULL CHECK (generation IN ('LTE', '5G')),
    monthly_price         INT,                         -- 후불: 월 요금
    charge_price          INT,                         -- 선불: 충전 금액
    valid_days            INT,                         -- 선불: 사용 기간
    discount_months       INT,                         -- 할인 기간
    price_after_discount  INT,                         -- 할인 후 요금 (할인 요금제 필수)
    tags                  TEXT[]       NOT NULL DEFAULT '{}',
    supplemented_fields   TEXT[]       NOT NULL DEFAULT '{}',   -- 운영자가 보완 입력한 항목
    collected_on          DATE,
    publish_at            TIMESTAMPTZ,                 -- 게시 예약 일시
    published_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (plan_id, version_no),
    CHECK (monthly_price IS NOT NULL OR charge_price IS NOT NULL)
);

ALTER TABLE plan
    ADD CONSTRAINT fk_plan_published_version FOREIGN KEY (published_version_id) REFERENCES plan_version(id);

-- 이달의 요금제 (제휴사 "이달의 요금제" URL 수집 결과 → 게시 중 요금제와 연결)
-- 프로모션관리(PR-03) 보류 중: 순서 = 제휴사 사이트 순서, 배지 없음
CREATE TABLE monthly_pick (
    id            BIGSERIAL    PRIMARY KEY,
    partner_code  VARCHAR(10)  NOT NULL REFERENCES partner(code),
    plan_id       BIGINT       REFERENCES plan(id),    -- NULL = 미매칭
    site_order    INT          NOT NULL DEFAULT 0,
    exposed       BOOLEAN      NOT NULL DEFAULT TRUE,
    collected_on  DATE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 인터넷 제휴업체 (PA-01 [인터넷])
CREATE TABLE internet_partner (
    id            BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    carrier       VARCHAR(5)   NOT NULL CHECK (carrier IN ('SKT', 'KT', 'LGU')),
    apply_url     VARCHAR(500),                        -- 기본 신청 페이지 (포워딩 대상)
    status        VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ENDED')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 인터넷 상품 (PR-02, 별도 등록)
CREATE TABLE internet_product (
    id                   BIGSERIAL    PRIMARY KEY,
    carrier              VARCHAR(5)   NOT NULL CHECK (carrier IN ('SKT', 'KT', 'LGU')),
    product_type         VARCHAR(10)  NOT NULL CHECK (product_type IN ('SINGLE', 'BUNDLE')),
    name                 VARCHAR(100) NOT NULL,
    monthly_price        INT          NOT NULL,
    benefits             TEXT[]       NOT NULL DEFAULT '{}',
    internet_partner_id  BIGINT       REFERENCES internet_partner(id),
    apply_url            VARCHAR(500),                 -- 상품별 신청 URL (없으면 업체 기본 URL)
    sort_order           INT          NOT NULL DEFAULT 0,
    exposed              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 포워딩 이동 기록 (RC-01 개통신청 · RC-02 인터넷 신청). 개인정보 없음.
-- 데이터가 쌓이면 월 단위 파티션으로 전환한다.
CREATE TABLE forward_log (
    id            BIGSERIAL    PRIMARY KEY,
    kind          VARCHAR(10)  NOT NULL CHECK (kind IN ('PLAN', 'INTERNET')),
    target_id     BIGINT       NOT NULL,               -- plan.id 또는 internet_product.id
    partner_ref   VARCHAR(20),                         -- partner.code 또는 internet_partner.id
    plan_type     VARCHAR(10),                         -- PLAN일 때 후불 / 선불
    target_url    VARCHAR(500),
    from_page     VARCHAR(30),                         -- 유입 화면 (S2, MAIN ...)
    result        VARCHAR(10)  NOT NULL CHECK (result IN ('FORWARDED', 'ENDED', 'NO_URL', 'HIDDEN')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_forward_log_kind_created ON forward_log (kind, created_at);

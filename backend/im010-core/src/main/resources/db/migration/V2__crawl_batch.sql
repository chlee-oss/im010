-- im010 수집 배치 스키마 (BA-01 스케줄관리 · BA-02 요금제배치관리 · PA-01 수집 URL)
-- 제휴사 요금제 페이지는 카테고리 탭마다 URL이 따로 있어(rate_plan.do?type=T0xx) 유형별로 URL을 여러 개 등록한다.

-- 수집 URL: 유형당 1개 → 탭마다 여러 개
ALTER TABLE collect_url DROP CONSTRAINT collect_url_partner_code_url_type_key;
ALTER TABLE collect_url
    ADD COLUMN label       VARCHAR(50),                -- 제휴사 사이트의 탭 이름 (예: LTE 요금제)
    ADD COLUMN sort_order  INT NOT NULL DEFAULT 0;     -- 같은 요금제가 여러 탭에 있으면 앞 순서 탭의 값을 쓴다
-- 같은 유형에 같은 URL 중복 등록 금지 (후불 · 선불 사이 중복 금지는 V1 ux_collect_url_plan_url)
CREATE UNIQUE INDEX ux_collect_url_type_url ON collect_url (url_type, url);

-- 제휴사 사이트에서 발견한 미등록 탭. 새 탭이 보이면 알림, 운영자가 "수집 안 함"으로 확인하면 IGNORED
CREATE TABLE partner_tab (
    id             BIGSERIAL    PRIMARY KEY,
    partner_code   VARCHAR(10)  NOT NULL REFERENCES partner(code),
    url            VARCHAR(500) NOT NULL,
    label          VARCHAR(100),
    status         VARCHAR(10)  NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW', 'IGNORED')),
    first_seen_on  DATE         NOT NULL,
    last_seen_on   DATE         NOT NULL,
    UNIQUE (partner_code, url)
);

-- 요금제: 최종 수집일 · 판매 종료일
ALTER TABLE plan
    ADD COLUMN last_collected_on  DATE,
    ADD COLUMN ended_on           DATE;
CREATE UNIQUE INDEX ux_plan_partner_code ON plan (partner_code, plan_type, partner_plan_code)
    WHERE partner_plan_code IS NOT NULL;

-- 수집 스케줄 (BA-01 [수집 일정]): 제휴사별 하루 1회
CREATE TABLE crawl_schedule (
    partner_code  VARCHAR(10)  PRIMARY KEY REFERENCES partner(code),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    days          VARCHAR(30)  NOT NULL DEFAULT 'DAILY',   -- DAILY 또는 MON,TUE,WED ...
    run_time      TIME         NOT NULL DEFAULT '04:00',
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 수집 작업 큐: 스케줄 · 즉시 실행 · 재시도. 같은 제휴사 작업은 동시에 돌지 않는다
CREATE TABLE crawl_job (
    id            BIGSERIAL    PRIMARY KEY,
    partner_code  VARCHAR(10)  NOT NULL REFERENCES partner(code),
    trigger       VARCHAR(10)  NOT NULL CHECK (trigger IN ('SCHEDULE', 'MANUAL', 'RETRY')),
    url_types     TEXT[],                                  -- NULL = 등록된 전체 유형 (재시도는 실패한 유형만)
    attempt       INT          NOT NULL DEFAULT 1,
    run_on        DATE         NOT NULL,                   -- 수집 기준일
    due_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    status        VARCHAR(10)  NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED', 'RUNNING', 'DONE')),
    requested_by  VARCHAR(50),
    started_at    TIMESTAMPTZ,
    finished_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_crawl_job_schedule ON crawl_job (partner_code, run_on) WHERE trigger = 'SCHEDULE';
CREATE INDEX ix_crawl_job_queue ON crawl_job (status, due_at);

-- 수집 실행 (BA-01 [실행 이력]): 작업 1건 = 유형별 실행 여러 건
CREATE TABLE crawl_run (
    id               BIGSERIAL    PRIMARY KEY,
    job_id           BIGINT       NOT NULL REFERENCES crawl_job(id),
    partner_code     VARCHAR(10)  NOT NULL REFERENCES partner(code),
    url_type         VARCHAR(10)  NOT NULL CHECK (url_type IN ('POSTPAID', 'PREPAID', 'MONTHLY')),
    run_on           DATE         NOT NULL,
    result           VARCHAR(10)  NOT NULL CHECK (result IN ('SUCCESS', 'FAILED', 'ABNORMAL')),
    url_count        INT          NOT NULL DEFAULT 0,
    collected_count  INT          NOT NULL DEFAULT 0,      -- 중복 제거 후 건수
    new_count        INT          NOT NULL DEFAULT 0,
    changed_count    INT          NOT NULL DEFAULT 0,
    ended_count      INT          NOT NULL DEFAULT 0,
    message          TEXT,
    started_at       TIMESTAMPTZ  NOT NULL,
    finished_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_crawl_run_partner ON crawl_run (partner_code, url_type, run_on);

-- 배치 수집 건 (BA-02): 파싱 값 30일 보관 (결정 #28), 원본 HTML은 저장하지 않음 (결정 #7)
CREATE TABLE crawl_item (
    id                    BIGSERIAL    PRIMARY KEY,
    run_id                BIGINT       NOT NULL REFERENCES crawl_run(id) ON DELETE CASCADE,
    partner_code          VARCHAR(10)  NOT NULL REFERENCES partner(code),
    url_type              VARCHAR(10)  NOT NULL CHECK (url_type IN ('POSTPAID', 'PREPAID', 'MONTHLY')),
    item_key              VARCHAR(200) NOT NULL,           -- 제휴사 요금제 코드, 없으면 요금제명|망
    partner_plan_code     VARCHAR(100),
    plan_id               BIGINT       REFERENCES plan(id),
    change_type           VARCHAR(10)  NOT NULL CHECK (change_type IN ('NEW', 'CHANGED', 'UNCHANGED', 'ENDED')),
    status                VARCHAR(20)  NOT NULL CHECK (status IN ('RECORDED', 'REVIEW_PENDING', 'APPROVAL_REQUESTED',
                                                                  'APPROVED', 'EXCLUDED', 'SUPERSEDED', 'AUTO_APPLIED')),
    name                  VARCHAR(200),
    data_text             VARCHAR(100),
    data_gb               NUMERIC(8,2),
    qos_text              VARCHAR(50),
    voice_text            VARCHAR(50),
    sms_text              VARCHAR(50),
    network               VARCHAR(5)   CHECK (network IN ('SKT', 'KT', 'LGU')),
    generation            VARCHAR(5)   CHECK (generation IN ('LTE', '5G')),
    price                 INT,                             -- 후불: 월 요금 · 선불: 충전 금액
    discount_months       INT,
    price_after_discount  INT,
    detail_url            VARCHAR(500),                    -- 제휴사 요금제 상세 (개통 URL 기본값)
    source_url            VARCHAR(500),
    site_order            INT,
    changed_fields        TEXT[]       NOT NULL DEFAULT '{}',
    warnings              TEXT[]       NOT NULL DEFAULT '{}',
    value_hash            CHAR(64),
    reviewer              VARCHAR(50),
    reason                VARCHAR(500),
    collected_on          DATE         NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_crawl_item_open ON crawl_item (partner_code, url_type, item_key)
    WHERE status IN ('REVIEW_PENDING', 'APPROVAL_REQUESTED');
CREATE INDEX ix_crawl_item_run ON crawl_item (run_id);
CREATE INDEX ix_crawl_item_collected ON crawl_item (collected_on);

-- 이달의 요금제: 제휴사 요금제 코드로 수집 항목과 맞춘다
ALTER TABLE monthly_pick
    ADD COLUMN partner_plan_code  VARCHAR(100),
    ADD COLUMN crawl_item_id      BIGINT REFERENCES crawl_item(id) ON DELETE SET NULL;

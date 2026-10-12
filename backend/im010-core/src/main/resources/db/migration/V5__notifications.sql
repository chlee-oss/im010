-- im010 알림 (백오피스 7장): 알림함(outbox) → 배치가 메신저 채널 · 메일로 발송
-- 메신저는 채널(웹훅) 단위, 메일은 관리자별 수신 설정. 어떤 모듈이든 알림함에 넣기만 하고 발송은 배치가 맡는다.

-- 알림 종류 (7장)
--   CRAWL_SUMMARY 수집 결과 요약 · CRAWL_FAILURE 수집 실패 · 이상 · PLAN_ENDED 판매 종료 처리 · MONTHLY_REMOVED 메인 노출 제외
--   REVIEW_DELAY 점검 · 승인 지연 · PUBLISH_FAILURE 게시 예약 실패 · FORWARD_NO_URL 포워딩 URL 없음
--   STALE_DATA 기준일 경과 · TAB_CHANGED 사이트 탭 변경

CREATE TABLE notification (
    id          BIGSERIAL    PRIMARY KEY,
    alert_type  VARCHAR(20)  NOT NULL,
    level       VARCHAR(10)  NOT NULL CHECK (level IN ('INFO', 'WARN', 'URGENT')),
    title       VARCHAR(200) NOT NULL,
    body        TEXT,
    dedupe_key  VARCHAR(200),                       -- 같은 키는 하루 한 번만 (포워딩 URL 없음 등)
    status      VARCHAR(10)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'PARTIAL', 'FAILED', 'NO_TARGET')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at     TIMESTAMPTZ
);
CREATE INDEX ix_notification_pending ON notification (status, created_at);
CREATE INDEX ix_notification_dedupe ON notification (dedupe_key, created_at) WHERE dedupe_key IS NOT NULL;

-- 메신저 채널 (ST-02 [알림 채널]): 웹훅 주소는 비밀값이라 화면에는 가려서 보여 준다
CREATE TABLE notify_channel (
    id           BIGSERIAL    PRIMARY KEY,
    name         VARCHAR(50)  NOT NULL,
    kind         VARCHAR(10)  NOT NULL CHECK (kind IN ('SLACK', 'TEAMS', 'JANDI', 'WEBHOOK')),
    webhook_url  VARCHAR(1000) NOT NULL,
    alert_types  TEXT[]       NOT NULL DEFAULT '{}',
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_by   VARCHAR(50),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 관리자별 메일 수신 (ST-02 알림 수신 설정)
ALTER TABLE admin_user
    ADD COLUMN email        VARCHAR(100),
    ADD COLUMN mail_alerts  TEXT[] NOT NULL DEFAULT '{}';

-- 발송 기록: 채널 · 메일 주소마다 한 줄, 실패하면 다시 보낸다 (최대 3회)
CREATE TABLE notification_delivery (
    id               BIGSERIAL    PRIMARY KEY,
    notification_id  BIGINT       NOT NULL REFERENCES notification(id) ON DELETE CASCADE,
    channel_id       BIGINT       REFERENCES notify_channel(id) ON DELETE SET NULL,
    target           VARCHAR(200) NOT NULL,          -- 채널 이름 또는 메일 주소
    status           VARCHAR(10)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts         INT          NOT NULL DEFAULT 0,
    last_error       VARCHAR(500),
    next_try_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at          TIMESTAMPTZ
);
CREATE INDEX ix_delivery_due ON notification_delivery (status, next_try_at);

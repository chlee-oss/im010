-- im010 백오피스: 관리자 · 권한 (CM-01 · ST-01 · ST-02 · ST-03 · ST-05) + 점검 · 승인 · 게시 기록 (BA-02 · BA-03 · PR-01)

-- 프로그램 (ST-01): 좌측 메뉴와 권한의 기준. actions = 그 화면에서 줄 수 있는 동작 (4.1)
--   VIEW 조회 · EDIT 등록·수정 · DELETE 삭제 · REVIEW 점검 · APPROVE 승인 · DOWNLOAD 다운로드 · PRIVACY 개인정보 열람
CREATE TABLE program (
    id          VARCHAR(10)  PRIMARY KEY,                  -- PA-01, BA-02 ...
    menu_group  VARCHAR(20)  NOT NULL,
    name        VARCHAR(50)  NOT NULL,
    path        VARCHAR(100) NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    actions     TEXT[]       NOT NULL
);

-- 권한 그룹 (ST-03). system = 최고관리자 그룹 (모든 권한, 수정 · 삭제 불가)
CREATE TABLE admin_group (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(30)  NOT NULL UNIQUE,
    name        VARCHAR(50)  NOT NULL,
    system      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE group_permission (
    group_id    BIGINT       NOT NULL REFERENCES admin_group(id) ON DELETE CASCADE,
    program_id  VARCHAR(10)  NOT NULL REFERENCES program(id),
    actions     TEXT[]       NOT NULL,
    PRIMARY KEY (group_id, program_id)
);

-- 관리자 (ST-02). 비밀번호는 해시만, OTP(TOTP) 비밀키는 최초 로그인 때 등록
CREATE TABLE admin_user (
    id                    BIGSERIAL    PRIMARY KEY,
    login_id              VARCHAR(50)  NOT NULL UNIQUE,
    name                  VARCHAR(50)  NOT NULL,
    dept                  VARCHAR(50),
    phone                 VARCHAR(30),
    group_id              BIGINT       NOT NULL REFERENCES admin_group(id),
    status                VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETIRED')),
    password_hash         VARCHAR(200) NOT NULL,
    must_change_password  BOOLEAN      NOT NULL DEFAULT TRUE,
    otp_secret            VARCHAR(64),
    otp_enabled           BOOLEAN      NOT NULL DEFAULT FALSE,
    otp_last_step         BIGINT,                            -- 같은 OTP 번호 재사용 방지
    failed_count          INT          NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,                       -- 5회 실패 시 30분 잠금
    last_login_at         TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 접속 · 처리 이력 (ST-05). 수정 · 삭제하지 않는다
CREATE TABLE admin_audit (
    id          BIGSERIAL    PRIMARY KEY,
    admin_id    BIGINT       REFERENCES admin_user(id),
    login_id    VARCHAR(50),
    kind        VARCHAR(10)  NOT NULL CHECK (kind IN ('LOGIN', 'ACCESS', 'ACTION')),
    program_id  VARCHAR(10),
    action      VARCHAR(40)  NOT NULL,                      -- LOGIN_OK, LOGIN_FAIL, REVIEW, APPROVE, PUBLISH ...
    target      VARCHAR(200),
    detail      TEXT,
    ip          VARCHAR(45),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_admin_audit_created ON admin_audit (created_at);
CREATE INDEX ix_admin_audit_kind ON admin_audit (kind, created_at);

-- 배치 수집 건: 점검 · 승인 기록, 운영자가 고친 항목
ALTER TABLE crawl_item
    ADD COLUMN edited_fields  TEXT[]       NOT NULL DEFAULT '{}',
    ADD COLUMN memo           VARCHAR(500),
    ADD COLUMN reviewed_at    TIMESTAMPTZ,
    ADD COLUMN approver       VARCHAR(50),
    ADD COLUMN approved_at    TIMESTAMPTZ;

-- 요금제 버전: 어느 수집 건에서 왔는지 · 바뀐 항목 · 점검자 · 승인자 · 게시자, 대체된 미게시 버전
ALTER TABLE plan_version
    ADD COLUMN crawl_item_id   BIGINT REFERENCES crawl_item(id) ON DELETE SET NULL,
    ADD COLUMN changed_fields  TEXT[]  NOT NULL DEFAULT '{}',
    ADD COLUMN reviewer        VARCHAR(50),
    ADD COLUMN approver        VARCHAR(50),
    ADD COLUMN approved_at     TIMESTAMPTZ,
    ADD COLUMN published_by    VARCHAR(50),
    ADD COLUMN discarded_at    TIMESTAMPTZ,   -- 게시 전에 새 버전으로 대체됨
    ADD COLUMN source_hash     CHAR(64);      -- 승인한 수집값 지문: 같은 값이 다시 수집되면 변경 없음 (수집 기록 보관 기간과 무관)

-- 기본 프로그램 (1장 메뉴 구조)
INSERT INTO program (id, menu_group, name, path, sort_order, enabled, actions) VALUES
    ('PA-01', '제휴사관리', '제휴사관리',     '/partners',          10, TRUE,  '{VIEW,EDIT,DELETE,DOWNLOAD}'),
    ('PR-01', '상품관리',   '요금제관리',     '/plans',             20, TRUE,  '{VIEW,EDIT,APPROVE,DOWNLOAD}'),
    ('PR-02', '상품관리',   '인터넷관리',     '/internet',          21, TRUE,  '{VIEW,EDIT,APPROVE,DOWNLOAD}'),
    ('PR-03', '상품관리',   '프로모션관리',   '/promotions',        22, FALSE, '{VIEW,EDIT}'),
    ('BA-01', '배치관리',   '스케줄관리',     '/schedules',         30, TRUE,  '{VIEW,EDIT}'),
    ('BA-02', '배치관리',   '요금제배치관리', '/batch-items',       31, TRUE,  '{VIEW,EDIT,REVIEW,DOWNLOAD}'),
    ('BA-03', '배치관리',   '승인관리',       '/approvals',         32, TRUE,  '{VIEW,APPROVE}'),
    ('RC-01', '접수관리',   '알뜰폰접수신청', '/receipts/plans',    40, TRUE,  '{VIEW,DOWNLOAD}'),
    ('RC-02', '접수관리',   '인터넷접수신청', '/receipts/internet', 41, TRUE,  '{VIEW,DOWNLOAD}'),
    ('MB-01', '회원관리',   '회원관리',       '/members',           50, FALSE, '{VIEW,EDIT,PRIVACY,DOWNLOAD}'),
    ('ST-01', '환경설정',   '프로그램관리',   '/settings/programs', 60, TRUE,  '{VIEW,EDIT}'),
    ('ST-02', '환경설정',   '관리자관리',     '/settings/admins',   61, TRUE,  '{VIEW,EDIT}'),
    ('ST-03', '환경설정',   '권한관리',       '/settings/groups',   62, TRUE,  '{VIEW,EDIT,DELETE}'),
    ('ST-04', '환경설정',   '약관관리',       '/settings/terms',    63, TRUE,  '{VIEW,EDIT}'),
    ('ST-05', '환경설정',   '접속이력관리',   '/settings/audit',    64, TRUE,  '{VIEW,DOWNLOAD}'),
    ('ST-06', '환경설정',   'Footer관리',     '/settings/footer',   65, TRUE,  '{VIEW,EDIT}'),
    ('ST-07', '환경설정',   '1:1문의관리',    '/settings/inquiries', 66, TRUE, '{VIEW,EDIT}');

-- 기본 권한 그룹 (4.2). 최고관리자는 system 이라 권한표 없이 전체 허용
INSERT INTO admin_group (code, name, system) VALUES
    ('SUPER',     '최고관리자',   TRUE),
    ('CONTENT',   '콘텐츠 운영자', FALSE),
    ('RECEPTION', '접수 담당',    FALSE),
    ('VIEWER',    '조회 전용',    FALSE);

INSERT INTO group_permission (group_id, program_id, actions)
SELECT g.id, p.program_id, p.actions::TEXT[]
FROM admin_group g
JOIN (VALUES
    ('CONTENT',   'PA-01', '{VIEW,EDIT}'),
    ('CONTENT',   'PR-01', '{VIEW,EDIT,APPROVE}'),
    ('CONTENT',   'PR-02', '{VIEW,EDIT,APPROVE}'),
    ('CONTENT',   'BA-01', '{VIEW,EDIT}'),
    ('CONTENT',   'BA-02', '{VIEW,EDIT,REVIEW}'),
    ('CONTENT',   'BA-03', '{VIEW,APPROVE}'),
    ('CONTENT',   'RC-01', '{VIEW,DOWNLOAD}'),
    ('CONTENT',   'RC-02', '{VIEW,DOWNLOAD}'),
    ('CONTENT',   'ST-07', '{VIEW,EDIT}'),
    ('RECEPTION', 'RC-01', '{VIEW,DOWNLOAD}'),
    ('RECEPTION', 'RC-02', '{VIEW,DOWNLOAD}'),
    ('RECEPTION', 'ST-07', '{VIEW,EDIT}'),
    ('VIEWER',    'PA-01', '{VIEW}'),
    ('VIEWER',    'PR-01', '{VIEW}'),
    ('VIEWER',    'PR-02', '{VIEW}'),
    ('VIEWER',    'BA-01', '{VIEW}'),
    ('VIEWER',    'BA-02', '{VIEW}'),
    ('VIEWER',    'BA-03', '{VIEW}'),
    ('VIEWER',    'RC-01', '{VIEW}'),
    ('VIEWER',    'RC-02', '{VIEW}')
) AS p(group_code, program_id, actions) ON p.group_code = g.code;

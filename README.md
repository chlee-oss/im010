# im010 — 알뜰폰 요금비교

7개 제휴 알뜰폰 요금제를 비교하고 `개통하기`로 제휴사에 연결하는 서비스.
기획 문서: [`im010_홈페이지_기획서.md`](im010_홈페이지_기획서.md) · [`im010_하위페이지_기획서.md`](im010_하위페이지_기획서.md) · [`im010_백오피스_기획서.md`](im010_백오피스_기획서.md) · 목업 [`im010_홈페이지_목업_2.html`](im010_홈페이지_목업_2.html)

## 구성

| 영역 | 기술 |
|---|---|
| 서버 OS | Rocky Linux 9.7 |
| 웹 | nginx (React 정적 파일 + `/api` · `/go` 리버스 프록시) |
| WAS | Spring Boot 4.1 (Java 21) · MyBatis 4.1 · Gradle |
| DB | PostgreSQL (마이그레이션: Flyway) |
| 프런트 | React 19 + TypeScript + Vite |

```
im010/
├─ backend/                Gradle 멀티 모듈
│  ├─ im010-core           공통: MyBatis 매퍼 · SQL · DB 마이그레이션(db/migration) · 로컬 예시 데이터(db/seed)
│  ├─ im010-api            고객용 REST API + /go 포워딩 (8080)
│  ├─ im010-admin          백오피스 API (8081) — 관리자 로그인(OTP) · 권한 · PA-01 · PR-01 · BA-01~03
│  ├─ im010-batch          수집 배치 — 제휴사 요금제 크롤링(Jsoup) · 변경 감지 · 판매 종료
│  └─ db/init-local.sql    로컬 DB 계정 · DB 생성
├─ frontend/web            고객 화면 (React) — 목업을 컴포넌트로 옮김
├─ frontend/admin          백오피스 화면 (React, PC 전용)
└─ deploy/                 nginx · systemd · 환경 변수 예시
```

## 로컬 실행 (Windows 기준)

준비물: JDK 21, Node.js 20+, PostgreSQL 16+ (로컬 서비스)

1. **로컬 DB** — 설치된 PostgreSQL 16 프로그램으로 D 드라이브에 별도 클러스터를 만들어 쓴다 (기존 5432 서비스와 별개).
   - 위치 `D:\pgdata\im010`, 포트 **5433**, localhost 전용, 한국어 정렬(ICU ko-KR)
   - 관리자(postgres) 비밀번호: `D:\pgdata\postgres-superuser.txt` (저장소에 올리지 않음)
   - 로컬 전용 계정 `im010` / `im010_local`, DB `im010`
   - PC를 다시 켜면 서버를 직접 켠다 (윈도우 서비스로 등록하지 않음). 끌 때는 `start` 대신 `stop`
     ```
     "C:\Program Files\PostgreSQL\16\bin\pg_ctl.exe" -D D:\pgdata\im010 -l D:\pgdata\im010-server.log start
     ```
   - 처음부터 다시 만들 때: `initdb -D D:\pgdata\im010 -U postgres --pwfile=D:\pgdata\postgres-superuser.txt -E UTF8 --locale-provider=icu --icu-locale=ko-KR --locale=C --auth=scram-sha-256`
     → `postgresql.conf`에 `port = 5433`, `listen_addresses = 'localhost'` 추가 → 서버 시작
     → `psql -h localhost -p 5433 -U postgres -f backend\db\init-local.sql`

2. **API 서버** — 처음 실행할 때 Flyway가 테이블과 예시 데이터를 만든다.
   ```
   cd backend
   gradlew.bat :im010-api:bootRun --args="--spring.profiles.active=local"
   ```

3. **프런트**
   ```
   cd frontend\web
   npm install
   npm run dev
   ```
   http://localhost:5173 — `/api` · `/go` 는 Vite가 8080으로 넘긴다.

4. **수집 배치 (필요할 때만)** — 로컬에서는 스케줄이 꺼져 있다. 제휴사를 지정해 한 번 수집하고 끝난다.
   ```
   cd backend
   gradlew.bat :im010-batch:bootRun --args="--spring.profiles.active=local --im010.crawl.run-now=mv,nt"
   ```
   실제 제휴사 사이트에 요청한다 (같은 사이트 요청 사이 3초). 예시 데이터 요금제는 제휴사 요금제 코드가 없어 수집하면 판매 종료로 바뀐다 —
   예시 화면을 유지하려면 별도 DB(`--spring.datasource.url=...`)로 실행한다.

5. **백오피스** — API(8081)와 화면(5174). API 서버(1번)를 먼저 한 번 실행해 테이블이 만들어져 있어야 한다.
   ```
   cd backend
   gradlew.bat :im010-admin:bootRun --args="--spring.profiles.active=local"
   ```
   ```
   cd frontend\admin
   npm install
   npm run dev
   ```
   http://localhost:5174 — 관리자가 없으면 로컬 초기 계정 `admin`이 만들어진다 (초기 비밀번호는 `backend/im010-admin/src/main/resources/application-local.yml`).
   첫 로그인 때 OTP 앱(Google Authenticator 등) 등록과 비밀번호 변경을 거친다.

테스트: `cd backend && gradlew.bat test` (DB 없이 실행되는 서비스 · 컨트롤러 · 파서 · 변경 감지 · OTP · 권한 테스트)

## 고객용 API (im010-api)

| 메서드 · 경로 | 설명 |
|---|---|
| `GET /api/partners` | 노출 중인 제휴사 · 브랜드 칩 색상 · 홈페이지 |
| `GET /api/plans?type=POSTPAID\|PREPAID&partner=` | 게시 중인 요금제, 가격순 |
| `GET /api/plans/cheapest?network=SKT\|KT\|LGU&dataGb=` | 계산기 — 후불 최저가 (`dataGb` 생략 = 무제한, 없으면 204) |
| `GET /api/plans/{id}` | S2 상세 + 비슷한 요금제(후불만) |
| `GET /api/monthly-plans` | 이달의 요금제 (제휴사 수집, 최대 7개, 배지 없음) |
| `GET /api/internet-products?type=SINGLE\|BUNDLE` | 인터넷 상품 |
| `GET /api/stats/summary` | 비교 가능한 요금제 수 (게시 중 후불) |
| `GET /go/{planId}?from=` | 개통하기 → 제휴사 개통 URL (302, RC-01 기록) |
| `GET /go/internet/{productId}?from=` | 인터넷 신청 → 제휴업체 신청 URL (302, RC-02 기록) |

- 오류 응답은 RFC 9457 ProblemDetail (`400` · `404`)
- `/go` 는 DB에 등록된 URL로만 이동한다 (요청 값으로 받은 URL로 보내지 않음). 이동 기록은 비동기 저장, 개인정보 없음.

## 수집 배치 (im010-batch)

| 단계 | 내용 |
|---|---|
| 스케줄 | 1분마다 `crawl_schedule`(BA-01, 기본 매일 04:00)을 확인해 `crawl_job` 큐에 넣고 처리. 같은 제휴사는 동시에 돌지 않음 |
| 수집 | 제휴사 · 유형(후불 → 선불 → 이달의 요금제)별 등록 URL(탭마다 1개)을 모두 받아 합침. 여러 탭에 있는 요금제는 요금제 코드(`no`)로 1건 |
| 파서 | `RatePlanParser` — 7개사 공통 플랫폼의 카드형 · 목록형 두 가지 구조 |
| 보호 조건 | 접속 실패 · URL 하나라도 0건 · 필수 항목 실패 20% 초과 · 직전 대비 50% 넘게 감소 → 아무것도 반영하지 않음 + 알림 |
| 변경 감지 | 신규 · 변경 → `crawl_item` 점검 대기(BA-02), 같은 값은 기록만, 사라진 요금제는 자동 판매 종료 + 이달의 요금제에서 제외 |
| 재시도 | 실패한 유형만 30분 뒤, 최대 2회 |
| 탭 감시 | 사이트 메뉴에 등록되지 않은 새 탭이 보이면 알림 (`partner_tab`, 수집 안 함으로 확인하면 IGNORED) |
| 알림 | `Notifier` — 사내 메신저 연동 전까지 로그(`im010.alert`) |
| 보관 | 처리 끝난 수집 건은 30일 뒤 삭제 (매일 03:30) |

| 예약 게시 | 1분마다 게시 예약 시각이 지난 버전을 게시 (게시 중 버전 교체), 실패하면 알림 |

## 백오피스 (im010-admin · frontend/admin)

| 화면 | 내용 |
|---|---|
| CM-01 로그인 | 아이디 · 비밀번호 → OTP(TOTP, 처음이면 QR 등록) → 최초 로그인 비밀번호 변경. 5회 실패 30분 잠금, 30분 미사용 로그아웃 |
| 권한 | 권한 그룹 × 프로그램(ST-01) × 동작(조회 · 등록·수정 · 삭제 · 점검 · 승인 · 다운로드). 기본 그룹: 최고관리자 · 콘텐츠 운영자 · 접수 담당 · 조회 전용 |
| BA-02 요금제배치관리 | 점검 대기 · 승인 요청 · 변경 없음 · 판매 종료 · 제외. 수집값 수정, 점검 완료(→ 승인 요청), 제외(사유), 판매 재개 요청 |
| BA-03 승인관리 | 승인 = 요금제 · 버전 생성 후 PR-01 "게시 대기" (선택: 게시 예약 함께), 반려(사유) |
| PR-01 요금제관리 | 보완 입력 · 개통 URL · 게시 예약(10분 단위) · 즉시 게시 · 예약 취소 · 비노출 · 롤백(7일) · 버전 이력 |
| PA-01 제휴사관리 | 제휴사 정보 · 수집 URL(유형별 탭마다) 등록 · [테스트] · 사이트 탭 확인(미확인 · 수집 안 함) |
| BA-01 스케줄관리 | 제휴사별 주기 · 시각 · 사용, 즉시 실행, 실행 이력 |
| ST-05 이력 | 로그인 · 점검 · 승인 · 게시 · URL 변경을 `admin_audit`에 기록 (화면은 3-2단계) |

- 세션 쿠키(HttpOnly · SameSite=Strict · 운영 Secure) + CSRF(쿠키 → `X-XSRF-TOKEN` 헤더)
- 승인한 수집값의 지문을 버전에 남겨, 운영자가 값을 고쳐 게시해도 같은 수집값이 다시 들어오면 변경 없음으로 본다

## 운영 서버 (Rocky Linux 9.7) 메모

- PostgreSQL은 OS 기본 저장소 대신 **PGDG 공식 저장소**에서 설치 (17 이상 권장)
- Java 21: `dnf install java-21-openjdk-headless`
- **SELinux**: nginx → 8080 프록시 허용 `setsebool -P httpd_can_network_connect 1`
- **firewalld**: 80/443만 외부 공개, 8080 · 8081 · 5432는 내부만
- 배포 파일
  - nginx: [`im010.conf`](deploy/nginx/im010.conf)(고객) · [`im010-admin.conf`](deploy/nginx/im010-admin.conf)(백오피스, 별도 주소 · 사내 IP 제한 선택) · [`im010-proxy.inc`](deploy/nginx/im010-proxy.inc)
  - systemd: [`im010-api`](deploy/systemd/im010-api.service) · [`im010-admin`](deploy/systemd/im010-admin.service) · [`im010-batch`](deploy/systemd/im010-batch.service) (배치는 한 대에서만)
  - 환경 변수 예시: [`api.env`](deploy/api.env.example) · [`admin.env`](deploy/admin.env.example) · [`batch.env`](deploy/batch.env.example)
- 백오피스 첫 계정: `IM010_ADMIN_BOOTSTRAP_PASSWORD`를 넣고 시작 → 첫 로그인(OTP · 비밀번호 변경) 후 값을 지운다
- 운영 DB에는 `db/seed`(예시 데이터)가 실행되지 않는다 — `local` 프로필에서만 포함

## 검색 노출 (React SPA)

고객 화면은 React SPA라 검색엔진이 요금제 목록 · 상세를 읽기 어렵다. 1차 오픈 전에 아래 중 하나를 적용한다.
1. 메인 · 요금제 상세를 빌드 시 미리 렌더링 (pre-render)
2. 필요하면 Next.js(SSR)로 전환 — API는 그대로 사용 가능

## 다음 단계

1. ~~제휴사 요금제 페이지 구조 확인~~ → Jsoup 확정 (서버 렌더링 HTML, 7개사 같은 플랫폼)
2. ~~im010-batch: 수집 · 변경 감지 · 판매 종료(보호 조건) · 스케줄~~
3. ~~3-1 백오피스 핵심: 로그인(OTP) · 권한 · PA-01 · BA-01~03 · PR-01 · 예약 게시~~
4. 3-2 백오피스: ST-01~07(프로그램 · 관리자 · 권한 · 약관 · 접속이력 · Footer · FAQ), RC-01 · RC-02 접수관리, PR-02 인터넷관리 · PA-01 [인터넷], 게시 일정 달력, 엑셀 다운로드, 사내 IP 제한
5. 알림: 사내 메신저 연동 (`Notifier` 구현 추가)

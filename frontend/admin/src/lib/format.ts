// 화면 표기

export const URL_TYPE: Record<string, string> = { POSTPAID: '후불', PREPAID: '선불', MONTHLY: '이달의 요금제' }
export const NETWORK: Record<string, string> = { SKT: 'SKT', KT: 'KT', LGU: 'LG U+' }

export const CHANGE: Record<string, string> = { NEW: '신규', CHANGED: '변경', UNCHANGED: '변경 없음', ENDED: '판매 종료' }

export const ITEM_STATUS: Record<string, string> = {
  RECORDED: '기록',
  REVIEW_PENDING: '점검 대기',
  APPROVAL_REQUESTED: '승인 요청',
  APPROVED: '승인',
  EXCLUDED: '제외',
  SUPERSEDED: '대체됨',
  AUTO_APPLIED: '자동 처리',
}

export const PLAN_STATE: Record<string, string> = {
  PUBLISHED: '게시 중',
  DRAFT: '게시 대기',
  SCHEDULED: '게시 예약',
  ENDED: '판매 종료',
  HIDDEN: '비노출',
}

export const RUN_RESULT: Record<string, string> = { SUCCESS: '성공', FAILED: '실패', ABNORMAL: '이상' }
export const TRIGGER: Record<string, string> = { SCHEDULE: '스케줄', MANUAL: '즉시 실행', RETRY: '재시도' }

/** 항목 이름 (수집 건 changedFields · editedFields, 버전 supplementedFields 공통) */
export const FIELD: Record<string, string> = {
  name: '요금제명',
  dataText: '데이터',
  dataGb: '기본 제공량',
  qos: 'QoS',
  voice: '통화',
  sms: '문자',
  network: '망',
  generation: '세대',
  price: '요금',
  discountMonths: '할인 기간',
  priceAfterDiscount: '할인 후 요금',
  validDays: '사용 기간',
  tags: '혜택 태그',
}

export const WARNING: Record<string, string> = {
  MISSING_NAME: '요금제명 미수집',
  MISSING_PRICE: '요금 미수집',
  PRICE_ZERO: '0원',
  MISSING_NETWORK: '망 미수집',
  MISSING_DATA: '기본 제공량 미수집',
  PRICE_JUMP: '요금 ±30% 초과',
  RESUMED: '판매 재개',
  SAME_AS_EXCLUDED: '제외된 값과 동일',
  UNMATCHED: '연결할 요금제 없음',
}

export const DAYS: [string, string][] = [
  ['MON', '월'],
  ['TUE', '화'],
  ['WED', '수'],
  ['THU', '목'],
  ['FRI', '금'],
  ['SAT', '토'],
  ['SUN', '일'],
]

export function won(n: number | null | undefined): string {
  return n === null || n === undefined ? '–' : n.toLocaleString('ko-KR')
}

const dt = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

/** 10.08 14:02 */
export function dateTime(iso: string | null | undefined): string {
  if (!iso) return '–'
  const parts = Object.fromEntries(dt.formatToParts(new Date(iso)).map((p) => [p.type, p.value]))
  return `${parts.month}.${parts.day} ${parts.hour}:${parts.minute}`
}

/** 오늘 (서울) yyyy-MM-dd */
export function today(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(new Date())
}

/** datetime-local 기본값: 다음 정각 (10분 단위 예약) */
export function nextHourLocal(): string {
  const d = new Date(Date.now() + 3600_000)
  d.setMinutes(0, 0, 0)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:00`
}

/** datetime-local 값 → 서버용 ISO */
export function localToIso(local: string): string {
  return new Date(local).toISOString()
}

export function itemValueText(field: string, v: Record<string, unknown> | null | undefined): string {
  if (!v) return '–'
  const raw = v[field]
  if (raw === null || raw === undefined || raw === '') return '–'
  if (field === 'price' || field === 'priceAfterDiscount') return won(raw as number) + '원'
  if (field === 'discountMonths') return raw + '개월'
  if (field === 'dataGb') return raw + 'GB'
  if (field === 'network') return NETWORK[raw as string] ?? String(raw)
  return String(raw)
}

export const ACTION: Record<string, string> = {
  VIEW: '조회',
  EDIT: '등록·수정',
  DELETE: '삭제',
  REVIEW: '점검',
  APPROVE: '승인',
  DOWNLOAD: '다운로드',
  PRIVACY: '개인정보 열람',
}

export const TERMS_TYPE: Record<string, string> = {
  SERVICE: '이용약관',
  PRIVACY: '개인정보처리방침',
  COLLECT: '개인정보 수집 · 이용 동의',
  THIRD_PARTY: '제3자 제공 동의',
  MARKETING: '마케팅 수신 동의',
}

export const RECEIPT_RESULT: Record<string, string> = { FORWARDED: '이동 완료', ENDED: '판매 종료', NO_URL: 'URL 없음', HIDDEN: '비노출' }
export const RECEIPT_CATEGORY: Record<string, string> = { POSTPAID: '후불', PREPAID: '선불', SINGLE: '단독', BUNDLE: '결합' }
export const AUDIT_KIND: Record<string, string> = { LOGIN: '로그인', ACCESS: '화면 접속', ACTION: '처리' }

/** 2026-10-08T05:02:00Z → 2026-10-08 14:02 (서울) */
export function fullDateTime(iso: string | null | undefined): string {
  if (!iso) return '–'
  const d = new Date(iso)
  const p = Object.fromEntries(
    new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })
      .formatToParts(d)
      .map((x) => [x.type, x.value]),
  )
  return `${p.year}-${p.month}-${p.day} ${p.hour}:${p.minute}`
}

/** yyyy-MM-dd 에 일수 더하기 */
export function addDays(date: string, days: number): string {
  const d = new Date(date + 'T00:00:00Z')
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}

export const CHANNEL_KIND: Record<string, string> = { SLACK: 'Slack · Mattermost', TEAMS: 'Microsoft Teams', JANDI: '잔디', WEBHOOK: '일반 웹훅' }
export const NOTIFY_STATUS: Record<string, string> = { PENDING: '발송 대기', SENT: '발송 완료', PARTIAL: '일부 실패', FAILED: '실패', NO_TARGET: '받을 곳 없음' }

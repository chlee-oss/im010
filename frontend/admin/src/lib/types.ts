// im010-admin 응답 타입 (backend/im010-admin 의 record 와 같은 모양)

export type Action = 'VIEW' | 'EDIT' | 'DELETE' | 'REVIEW' | 'APPROVE' | 'DOWNLOAD' | 'PRIVACY'
export type UrlType = 'POSTPAID' | 'PREPAID' | 'MONTHLY'
export type Stage = 'OTP_SETUP' | 'OTP' | 'PASSWORD_CHANGE' | 'DONE' | null

export interface MenuItem {
  id: string
  name: string
  path: string
  actions: Action[]
}

export interface Me {
  loginId: string
  name: string
  groupName: string
  menus: { group: string; items: MenuItem[] }[]
  badges: { reviewPending: number; approvalRequested: number; scheduledToday: number }
}

export interface ItemValues {
  name: string | null
  dataText: string | null
  dataGb: number | null
  qosText: string | null
  voiceText: string | null
  smsText: string | null
  network: string | null
  generation: string | null
  price: number | null
  discountMonths: number | null
  priceAfterDiscount: number | null
}

export interface Item {
  id: number
  partnerCode: string
  partnerName: string
  urlType: UrlType
  changeType: 'NEW' | 'CHANGED' | 'UNCHANGED' | 'ENDED'
  status: 'RECORDED' | 'REVIEW_PENDING' | 'APPROVAL_REQUESTED' | 'APPROVED' | 'EXCLUDED' | 'SUPERSEDED' | 'AUTO_APPLIED'
  planId: number | null
  partnerPlanCode: string | null
  values: ItemValues
  detailUrl: string | null
  sourceUrl: string | null
  siteOrder: number | null
  changedFields: string[]
  warnings: string[]
  editedFields: string[]
  reviewer: string | null
  reviewedAt: string | null
  approver: string | null
  approvedAt: string | null
  reason: string | null
  memo: string | null
  collectedOn: string
}

export interface ItemPage {
  collectedOn: string
  summary: Record<string, number>
  total: number
  page: number
  items: Item[]
}

export interface Version {
  id: number
  versionNo: number
  state: 'PUBLISHED' | 'SCHEDULED' | 'DRAFT' | 'DISCARDED'
  name: string
  dataText: string
  dataGb: number | null
  qosText: string | null
  voiceText: string | null
  smsText: string | null
  network: string
  generation: string
  monthlyPrice: number | null
  chargePrice: number | null
  validDays: number | null
  discountMonths: number | null
  priceAfterDiscount: number | null
  tags: string[]
  supplementedFields: string[]
  changedFields: string[]
  collectedOn: string | null
  publishAt: string | null
  publishedAt: string | null
  publishedBy: string | null
  reviewer: string | null
  approver: string | null
  approvedAt: string | null
  crawlItemId: number | null
}

export interface PlanRow {
  id: number
  partnerCode: string
  partnerName: string
  planType: 'POSTPAID' | 'PREPAID'
  partnerPlanCode: string | null
  status: string
  activationUrl: string | null
  publishedVersionId: number | null
  draftVersionId: number | null
  name: string
  price: number | null
  network: string
  publishedAt: string | null
  publishAt: string | null
  lastCollectedOn: string | null
  endedOn: string | null
}

export type PlanState = 'PUBLISHED' | 'DRAFT' | 'SCHEDULED' | 'ENDED' | 'HIDDEN'

export interface PlanDetail {
  plan: PlanRow
  state: PlanState
  published: Version | null
  draft: Version | null
  versions: Version[]
  missing: string[]
  canRollback: boolean
}

export interface BulkResult {
  done: number[]
  skipped: { id: number; reason: string }[]
}

export interface Partner {
  code: string
  name: string
  chipBg: string
  chipFg: string
  homepageUrl: string | null
  exposed: boolean
  sortOrder: number
  postpaidUrls: number
  prepaidUrls: number
  monthlyUrls: number
  newTabs: number
  scheduleEnabled: boolean | null
  scheduleDays: string | null
  scheduleTime: string | null
  lastRunAt: string | null
  lastRunResult: 'SUCCESS' | 'FAILED' | 'ABNORMAL' | null
  jobWaiting: boolean
}

export interface CollectUrl {
  id: number
  partnerCode: string
  urlType: UrlType
  url: string
  label: string | null
  sortOrder: number
}

export interface PartnerTab {
  id: number
  partnerCode: string
  url: string
  label: string | null
  status: 'NEW' | 'IGNORED'
}

export interface Run {
  id: number
  jobId: number
  trigger: 'SCHEDULE' | 'MANUAL' | 'RETRY'
  attempt: number
  partnerCode: string
  partnerName: string
  urlType: UrlType
  runOn: string
  result: 'SUCCESS' | 'FAILED' | 'ABNORMAL'
  urlCount: number
  collectedCount: number
  newCount: number
  changedCount: number
  endedCount: number
  message: string | null
  startedAt: string
  finishedAt: string
}

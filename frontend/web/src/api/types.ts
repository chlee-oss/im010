// im010-api 응답 타입 (backend/im010-api 의 record 와 같은 모양)

export type PlanType = 'POSTPAID' | 'PREPAID'
export type NetworkCode = 'SKT' | 'KT' | 'LGU'

export interface Partner {
  code: string
  name: string
  chipBg: string
  chipFg: string
  homepageUrl: string | null
}

export interface PlanSummary {
  id: number
  partnerCode: string
  partnerName: string
  planType: PlanType
  name: string
  dataText: string
  unlimited: boolean
  dataGb: number | null
  network: NetworkCode
  networkLabel: string
  generation: 'LTE' | '5G'
  monthlyPrice: number | null
  chargePrice: number | null
  validDays: number | null
  qos: string | null
  tags: string[]
  voice: string | null
  sms: string | null
  discountMonths: number | null
  priceAfterDiscount: number | null
  /** 메인 "제휴사별 요금제" 노출 순서 (백오피스 PR-01 지정), 없으면 null */
  mainPick: 1 | 2 | null
}

export interface PlanDetail {
  plan: PlanSummary
  voice: string | null
  sms: string | null
  discountMonths: number | null
  priceAfterDiscount: number | null
  ended: boolean
  basisDate: string | null
  activatePath: string
  similar: PlanSummary[]
}

export interface InternetProduct {
  id: number
  carrier: NetworkCode
  carrierLabel: string
  productType: 'SINGLE' | 'BUNDLE'
  name: string
  monthlyPrice: number
  benefits: string[]
  applyPath: string
}

export interface StatsSummary {
  comparablePlanCount: number
}

export type TermsType = 'SERVICE' | 'PRIVACY' | 'COLLECT' | 'THIRD_PARTY' | 'MARKETING'

export interface Terms {
  type: TermsType
  version: string
  effectiveOn: string
  body: string
  versions: { id: number; version: string; effectiveOn: string; current: boolean }[]
}

export interface FooterInfo {
  companyName: string
  ceo: string
  businessNo: string
  mailOrderNo: string | null
  address: string
  csPhone: string
  csHours: string | null
  email: string | null
  notice: string | null
}

export interface Faq {
  id: number
  category: string
  question: string
  answer: string
}

// 알뜰AI
export interface AiCategory {
  code: string
  name: string
  slug: string
  bgColor: string
  accentColor: string
}

export interface AiCard {
  id: number
  categorySlug: string
  categoryName: string
  bgColor: string
  accentColor: string
  title: string
  slug: string
  cardSummary: string | null
  thumbnailUrl: string | null
  tags: string[]
  sponsored: boolean
  readMinutes: number
  publishedAt: string
  isNew: boolean
}

export interface AiPage {
  pinned: AiCard | null
  total: number
  page: number
  size: number
  items: AiCard[]
}

export interface AiSource {
  name: string | null
  title: string | null
  date: string | null
  url: string | null
}

export interface AiPost {
  id: number
  categorySlug: string
  categoryName: string
  bgColor: string
  accentColor: string
  title: string
  slug: string
  cardSummary: string | null
  thumbnailUrl: string | null
  bodyHtml: string
  aiSummary: string[]
  tags: string[]
  sources: AiSource[]
  sponsored: boolean
  readMinutes: number
  authorName: string
  publishedAt: string
  contentUpdatedAt: string | null
  seoTitle: string | null
  seoDescription: string | null
}

export interface AiDetail {
  post: AiPost
  relatedPlans: PlanSummary[]
  relatedInternet: InternetProduct[]
  older: AiCard | null
  newer: AiCard | null
  sameCategory: AiCard[]
}

import type {
  AiCategory,
  AiDetail,
  AiPage,
  Faq,
  FooterInfo,
  InternetProduct,
  NetworkCode,
  Partner,
  PlanDetail,
  PlanSummary,
  PlanType,
  StatsSummary,
  Terms,
  TermsType,
} from './types'

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function getJson<T>(path: string, signal?: AbortSignal): Promise<T | null> {
  const res = await fetch(path, { headers: { Accept: 'application/json' }, signal })
  if (res.status === 204) return null
  if (!res.ok) throw new ApiError(res.status, `${res.status} ${path}`)
  return (await res.json()) as T
}

function qs(params: Record<string, string | number | undefined | null>): string {
  const sp = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') sp.set(k, String(v))
  }
  const s = sp.toString()
  return s ? `?${s}` : ''
}

export const api = {
  partners: (signal?: AbortSignal) => getJson<Partner[]>('/api/partners', signal),

  plans: (type: PlanType, partner?: string, signal?: AbortSignal) =>
    getJson<PlanSummary[]>(`/api/plans${qs({ type, partner })}`, signal),

  /** dataGb 가 null 이면 무제한. 조건에 맞는 요금제가 없으면 null. */
  cheapest: (network: NetworkCode, dataGb: number | null, signal?: AbortSignal) =>
    getJson<PlanSummary>(`/api/plans/cheapest${qs({ network, dataGb })}`, signal),

  planDetail: (id: number, signal?: AbortSignal) => getJson<PlanDetail>(`/api/plans/${id}`, signal),

  monthlyPlans: (signal?: AbortSignal) => getJson<PlanSummary[]>('/api/monthly-plans', signal),

  internetProducts: (type?: 'SINGLE' | 'BUNDLE', signal?: AbortSignal) =>
    getJson<InternetProduct[]>(`/api/internet-products${qs({ type })}`, signal),

  statsSummary: (signal?: AbortSignal) => getJson<StatsSummary>('/api/stats/summary', signal),

  /** 약관: id 가 없으면 시행 중인 버전 */
  terms: (type: TermsType, id?: number, signal?: AbortSignal) => getJson<Terms>(`/api/terms/${type}${qs({ id })}`, signal),

  footer: (signal?: AbortSignal) => getJson<FooterInfo>('/api/footer', signal),

  faqs: (signal?: AbortSignal) => getJson<Faq[]>('/api/faqs', signal),

  aiCategories: (signal?: AbortSignal) => getJson<AiCategory[]>('/api/ai/categories', signal),

  /** category = 카테고리 주소 (mobile · internet · misc), sort = latest | popular */
  aiPosts: (p: { category?: string; tag?: string; q?: string; sort?: string; page?: number }, signal?: AbortSignal) =>
    getJson<AiPage>(`/api/ai/posts${qs(p)}`, signal),

  aiPost: (id: number, signal?: AbortSignal) => getJson<AiDetail>(`/api/ai/posts/${id}`, signal),

  /** 조회 수 (실패해도 화면에는 영향 없음) */
  aiView: (id: number) => fetch(`/api/ai/posts/${id}/view`, { method: 'POST' }).catch(() => undefined),
}

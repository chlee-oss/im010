// 백오피스 API 호출. 세션 쿠키 인증 + CSRF(XSRF-TOKEN 쿠키 → X-XSRF-TOKEN 헤더)

export class ApiError extends Error {
  status: number
  code: string | undefined

  constructor(status: number, code: string | undefined, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

function xsrfToken(): string | undefined {
  const c = document.cookie.split('; ').find((v) => v.startsWith('XSRF-TOKEN='))
  return c ? decodeURIComponent(c.slice('XSRF-TOKEN='.length)) : undefined
}

export async function api<T>(method: 'GET' | 'POST' | 'PUT' | 'DELETE', path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const token = xsrfToken()
  if (method !== 'GET' && token) headers['X-XSRF-TOKEN'] = token
  const init: RequestInit = { method, headers, credentials: 'same-origin' }
  if (body !== undefined) init.body = JSON.stringify(body)
  const res = await fetch('/admin/api' + path, init)
  if (res.status === 204) return undefined as T
  const data = await res.json().catch(() => null)
  if (!res.ok) {
    if (res.status === 401 && !path.startsWith('/auth')) {
      window.dispatchEvent(new Event('im010:unauthorized'))
    }
    throw new ApiError(res.status, data?.code, data?.detail ?? '요청을 처리하지 못했습니다 (' + res.status + ')')
  }
  return data as T
}

export const get = <T>(path: string) => api<T>('GET', path)
export const post = <T>(path: string, body?: unknown) => api<T>('POST', path, body ?? {})
export const put = <T>(path: string, body: unknown) => api<T>('PUT', path, body)
export const del = <T>(path: string) => api<T>('DELETE', path)

/** 쿼리 문자열 (빈 값은 뺀다) */
export function qs(params: Record<string, string | number | undefined | null>): string {
  const p = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') p.set(k, String(v))
  }
  const s = p.toString()
  return s ? '?' + s : ''
}

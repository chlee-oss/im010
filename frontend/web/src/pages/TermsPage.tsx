import { useSearchParams, useParams } from 'react-router-dom'
import { api } from '../api/client'
import type { TermsType } from '../api/types'
import StateMessage from '../components/StateMessage'
import { useApi } from '../lib/useApi'
import { usePageTitle } from '../lib/usePageTitle'
import NotFoundPage from './NotFoundPage'

const TYPES: Record<string, [TermsType, string]> = {
  service: ['SERVICE', '이용약관'],
  privacy: ['PRIVACY', '개인정보처리방침'],
}

/** 이용약관 · 개인정보처리방침 (백오피스 ST-04). 이전 · 시행 예정 버전도 볼 수 있다. */
export default function TermsPage() {
  const { slug } = useParams()
  const entry = slug ? TYPES[slug] : undefined
  if (!entry) return <NotFoundPage />
  return <Terms type={entry[0]} title={entry[1]} />
}

function Terms({ type, title }: { type: TermsType; title: string }) {
  usePageTitle(title)
  const [params, setParams] = useSearchParams()
  const id = params.get('v') ? Number(params.get('v')) : undefined
  const { data, loading, error } = useApi((signal) => api.terms(type, id, signal), [type, id])
  // 시행 중 버전보다 시행일이 늦은 버전 = 시행 예정
  const currentOn = data?.versions.find((v) => v.current)?.effectiveOn ?? ''

  return (
    <section className="doc-page">
      <div className="wrap narrow">
        <h1>{title}</h1>
        {data ? (
          <>
            <div className="doc-meta">
              <span>
                버전 {data.version} · 시행일 {data.effectiveOn}
              </span>
              {data.versions.length > 1 && (
                <label>
                  다른 버전 보기{' '}
                  <select
                    value={id ?? data.versions.find((v) => v.current)?.id ?? ''}
                    onChange={(e) => setParams(e.target.value ? { v: e.target.value } : {})}
                  >
                    {data.versions.map((v) => (
                      <option key={v.id} value={v.id}>
                        {v.version} ({v.effectiveOn} 시행{v.current ? ' · 현재' : v.effectiveOn > currentOn ? ' 예정' : ''})
                      </option>
                    ))}
                  </select>
                </label>
              )}
            </div>
            <div className="doc-body">{data.body}</div>
          </>
        ) : error ? (
          <p className="state-msg">아직 게시된 {title}이 없어요.</p>
        ) : (
          <StateMessage loading={loading} />
        )}
      </div>
    </section>
  )
}

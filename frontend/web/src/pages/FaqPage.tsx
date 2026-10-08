import { useState } from 'react'
import { api } from '../api/client'
import StateMessage from '../components/StateMessage'
import { useApi } from '../lib/useApi'
import { usePageTitle } from '../lib/usePageTitle'

/** S7 자주 묻는 질문 (백오피스 ST-07 [FAQ]) */
export default function FaqPage() {
  usePageTitle('자주 묻는 질문')
  const { data, loading, error } = useApi((signal) => api.faqs(signal), [])
  const [category, setCategory] = useState<string | null>(null)
  const categories = [...new Set((data ?? []).map((f) => f.category))]
  const list = (data ?? []).filter((f) => !category || f.category === category)

  return (
    <section className="doc-page">
      <div className="wrap narrow">
        <h1>자주 묻는 질문</h1>
        {categories.length > 1 && (
          <div className="faq-cats" role="tablist">
            <button role="tab" aria-selected={!category} className={!category ? 'on' : ''} onClick={() => setCategory(null)}>
              전체
            </button>
            {categories.map((c) => (
              <button key={c} role="tab" aria-selected={category === c} className={category === c ? 'on' : ''} onClick={() => setCategory(c)}>
                {c}
              </button>
            ))}
          </div>
        )}
        <StateMessage loading={loading} error={error} />
        {data && list.length === 0 && <p className="state-msg">등록된 질문이 없어요.</p>}
        <div className="faq-list">
          {list.map((f) => (
            <details key={f.id} className="faq-item">
              <summary>
                <span className="faq-cat">{f.category}</span> {f.question}
              </summary>
              <div className="faq-answer">{f.answer}</div>
            </details>
          ))}
        </div>
      </div>
    </section>
  )
}

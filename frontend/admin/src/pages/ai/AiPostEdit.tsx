import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { Badge, Loading, Modal, PageHead, Tabs } from '../../components/ui'
import { del, get, post, put, upload } from '../../lib/api'
import { AI_STATE, dateTime, localToIso, nextHourLocal, won } from '../../lib/format'
import { useMe } from '../../lib/me'
import { useRun } from '../../lib/notice'
import type { AiCategory, AiPostDetail, AiRelatedOption, AiSource } from '../../lib/types'
import { useLoad } from '../../lib/useLoad'
import { AI_STATE_TONE } from './AiPosts'

interface Form {
  categoryCode: string
  title: string
  slug: string
  cardSummary: string
  thumbnailUrl: string
  bodyMd: string
  aiSummary: string[]
  tags: string
  sources: AiSource[]
  relatedPlanIds: number[]
  relatedInternetIds: number[]
  sponsored: boolean
  pinned: boolean
  seoTitle: string
  seoDescription: string
}

const EMPTY_SOURCE: AiSource = { name: '', title: '', date: '', url: '' }

function toForm(d: AiPostDetail): Form {
  const p = d.post
  return {
    categoryCode: p.categoryCode,
    title: p.title,
    slug: p.slug,
    cardSummary: p.cardSummary ?? '',
    thumbnailUrl: p.thumbnailUrl ?? '',
    bodyMd: p.bodyMd,
    aiSummary: [0, 1, 2].map((i) => d.aiSummary[i] ?? ''),
    tags: d.tags.join(', '),
    sources: d.sources,
    relatedPlanIds: d.relatedPlanIds,
    relatedInternetIds: d.relatedInternetIds,
    sponsored: p.sponsored,
    pinned: p.pinned,
    seoTitle: p.seoTitle ?? '',
    seoDescription: p.seoDescription ?? '',
  }
}

function toRequest(f: Form) {
  return {
    ...f,
    tags: f.tags
      .split(/[,\n]/)
      .map((t) => t.trim().replace(/^#/, ''))
      .filter(Boolean),
    aiSummary: f.aiSummary.map((s) => s.trim()).filter(Boolean),
    sources: f.sources.filter((s) => (s.name ?? '').trim() || (s.url ?? '').trim()),
  }
}

/** CT-01 알뜰AI 게시글관리 — 글쓰기 · 수정 */
export default function AiPostEdit() {
  const { id } = useParams()
  const [params] = useSearchParams()
  const isNew = id === undefined
  const categories = useLoad(() => get<AiCategory[]>('/contents/categories'), [])
  const detail = useLoad(() => (isNew ? Promise.resolve(null) : get<AiPostDetail>(`/contents/posts/${id}`)), [id])

  if (!categories.data || (!isNew && !detail.data)) return <Loading error={categories.error ?? detail.error} />
  const initial: Form = detail.data
    ? toForm(detail.data)
    : {
        categoryCode: params.get('category') ?? categories.data[0]?.code ?? '',
        title: '',
        slug: '',
        cardSummary: '',
        thumbnailUrl: '',
        bodyMd: '',
        aiSummary: ['', '', ''],
        tags: '',
        sources: [],
        relatedPlanIds: [],
        relatedInternetIds: [],
        sponsored: false,
        pinned: false,
        seoTitle: '',
        seoDescription: '',
      }
  return (
    <Editor
      key={detail.data ? detail.data.post.updatedAt : 'new'}
      detail={detail.data}
      initial={initial}
      categories={categories.data}
      onSaved={(d) => detail.setData(d)}
    />
  )
}

function Editor({
  detail,
  initial,
  categories,
  onSaved,
}: {
  detail: AiPostDetail | null
  initial: Form
  categories: AiCategory[]
  onSaved: (d: AiPostDetail) => void
}) {
  const navigate = useNavigate()
  const { can } = useMe()
  const { run, busy } = useRun()
  const [form, setForm] = useState<Form>(initial)
  const [tab, setTab] = useState<'WRITE' | 'PREVIEW'>('WRITE')
  const [preview, setPreview] = useState('')
  const [scheduling, setScheduling] = useState(false)
  const [publishAt, setPublishAt] = useState(nextHourLocal())
  const bodyRef = useRef<HTMLTextAreaElement>(null)
  const canEdit = can('CT-01', 'EDIT')
  const canApprove = can('CT-01', 'APPROVE')
  const canDelete = can('CT-01', 'DELETE')
  const dirty = JSON.stringify(form) !== JSON.stringify(initial)
  const category = categories.find((c) => c.code === form.categoryCode)
  const state = detail?.state ?? 'DRAFT'
  const p = detail?.post

  useEffect(() => {
    if (!dirty) return
    const warn = (e: BeforeUnloadEvent) => e.preventDefault()
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  useEffect(() => {
    if (tab !== 'PREVIEW') return
    let alive = true
    post<{ html: string }>('/contents/posts/preview', { bodyMd: form.bodyMd })
      .then((r) => alive && setPreview(r.html))
      .catch((e) => alive && setPreview(`<p>⚠ ${e instanceof Error ? e.message : e}</p>`))
    return () => {
      alive = false
    }
  }, [tab, form.bodyMd])

  const set = <K extends keyof Form>(key: K, value: Form[K]) => setForm((f) => ({ ...f, [key]: value }))

  const save = () =>
    run(
      () => (detail ? put<AiPostDetail>(`/contents/posts/${detail.post.id}`, toRequest(form)) : post<AiPostDetail>('/contents/posts', toRequest(form))),
      () => '저장했습니다',
    ).then((d) => {
      if (!d) return
      if (detail) onSaved(d)
      else navigate(`/contents/posts/${d.post.id}`, { replace: true })
    })

  const act = (task: () => Promise<AiPostDetail>, message: string) =>
    run(task, () => message).then((d) => {
      if (d) onSaved(d)
    })

  /** 본문 커서 위치에 넣기 (선택한 글자가 있으면 감싸기) */
  const insert = (before: string, after = '', placeholder = '') => {
    const el = bodyRef.current
    if (!el) return
    const { selectionStart: s, selectionEnd: e, value } = el
    const selected = value.slice(s, e) || placeholder
    const next = value.slice(0, s) + before + selected + after + value.slice(e)
    set('bodyMd', next)
    requestAnimationFrame(() => {
      el.focus()
      el.setSelectionRange(s + before.length, s + before.length + selected.length)
    })
  }

  const pickImage = (onUrl: (url: string) => void) => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = 'image/jpeg,image/png,image/webp,image/gif'
    input.onchange = () => {
      const file = input.files?.[0]
      if (file) void run(() => upload<{ url: string }>('/contents/uploads', file), () => '이미지를 올렸습니다').then((r) => r && onUrl(r.url))
    }
    input.click()
  }

  const title = detail ? '글 수정' : '글쓰기'
  return (
    <>
      <PageHead title={`알뜰AI ${title}`}>
        <Link to="/contents/posts" className="link">
          ‹ 목록
        </Link>
      </PageHead>

      <div className="ai-editor">
        <div className="ai-main">
          <section className="panel">
            <div className="form-grid">
              <label>
                카테고리
                <select value={form.categoryCode} onChange={(e) => set('categoryCode', e.target.value)} disabled={!canEdit}>
                  {categories.map((c) => (
                    <option key={c.code} value={c.code}>
                      {c.name}
                      {c.exposed ? '' : ' (비노출)'}
                    </option>
                  ))}
                </select>
              </label>
              <label className="ai-span2">
                주소용 이름 <span className="muted small">비우면 제목으로 만듭니다 · /ai/posts/{p?.id ?? '번호'}/이름</span>
                <input value={form.slug} onChange={(e) => set('slug', e.target.value)} maxLength={120} placeholder="예: esim-알뜰폰-개통" disabled={!canEdit} />
              </label>
              <label className="span2">
                제목 <span className="muted small">{form.title.length} / 120 · 카드에는 두 줄까지 보입니다</span>
                <input className="ai-title-input" value={form.title} onChange={(e) => set('title', e.target.value)} maxLength={120} disabled={!canEdit} />
              </label>
              <label className="span2">
                카드 요약 <span className="muted small">상단 고정 카드 · 검색 결과 설명에 씁니다 ({form.cardSummary.length} / 200)</span>
                <textarea rows={2} value={form.cardSummary} onChange={(e) => set('cardSummary', e.target.value)} maxLength={200} disabled={!canEdit} />
              </label>
            </div>
          </section>

          <section className="panel">
            <div className="panel-head">
              <Tabs value={tab} onChange={setTab} tabs={[['WRITE', '작성 (마크다운)'], ['PREVIEW', '미리보기']]} />
              {tab === 'WRITE' && canEdit && (
                <div className="ai-toolbar">
                  <button onClick={() => insert('**', '**', '굵게')} title="굵게">
                    <b>B</b>
                  </button>
                  <button onClick={() => insert('\n## ', '\n', '소제목')}>소제목</button>
                  <button onClick={() => insert('\n- ', '', '목록')}>목록</button>
                  <button onClick={() => insert('[', '](https://)', '링크 글자')}>링크</button>
                  <button onClick={() => insert('\n| 구분 | 내용 |\n| --- | --- |\n| ', ' |  |\n', '항목')}>표</button>
                  <button onClick={() => insert('\n> ', '\n', '강조할 문장')}>인용</button>
                  <button onClick={() => pickImage((url) => insert(`\n![`, `](${url})\n`, '이미지 설명'))}>🖼 이미지 넣기</button>
                </div>
              )}
            </div>
            {tab === 'WRITE' ? (
              <textarea
                ref={bodyRef}
                className="ai-body-input"
                value={form.bodyMd}
                onChange={(e) => set('bodyMd', e.target.value)}
                rows={26}
                disabled={!canEdit}
                placeholder={'본문을 마크다운으로 씁니다.\n\n## 소제목\n문단은 빈 줄로 나눕니다.\n\n- 목록\n- 목록'}
              />
            ) : (
              // 서버가 허용 태그만 남긴 HTML (게시될 내용과 같음)
              <div className="ai-body" dangerouslySetInnerHTML={{ __html: preview }} />
            )}
            <p className="muted small">
              {form.bodyMd.length.toLocaleString()}자 · 약 {Math.max(1, Math.round(form.bodyMd.length / 500))}분 읽기 · 스크립트 · 외부 삽입(iframe)은 게시할 때 빠집니다
            </p>
          </section>

          <section className="panel">
            <h2>
              ✨ AI 3줄 요약 <span className="muted small">본문 위에 "AI가 정리하고 편집팀이 확인했어요"로 보입니다. 운영자가 확인 · 수정한 문장만 씁니다 (AI 초안 만들기는 2차)</span>
            </h2>
            <div className="ai-lines">
              {form.aiSummary.map((s, i) => (
                <input
                  key={i}
                  value={s}
                  maxLength={80}
                  placeholder={`${i + 1}번째 줄 (80자 이하)`}
                  disabled={!canEdit}
                  onChange={(e) => set('aiSummary', form.aiSummary.map((v, j) => (j === i ? e.target.value : v)))}
                />
              ))}
            </div>
          </section>

          <section className="panel">
            <div className="panel-head">
              <h2>출처</h2>
              {canEdit && form.sources.length < 5 && (
                <button onClick={() => set('sources', [...form.sources, { ...EMPTY_SOURCE }])}>+ 출처 추가</button>
              )}
            </div>
            {form.sources.length === 0 ? (
              <p className="muted small">기사 · 보도자료를 바탕으로 썼다면 출처를 남겨 주세요. 상세 화면 본문 아래에 보입니다.</p>
            ) : (
              <div className="ai-sources">
                {form.sources.map((s, i) => {
                  const upd = (k: keyof AiSource, v: string) => set('sources', form.sources.map((x, j) => (j === i ? { ...x, [k]: v } : x)))
                  return (
                    <div key={i} className="ai-source-row">
                      <input placeholder="매체 (예: ○○일보)" value={s.name ?? ''} onChange={(e) => upd('name', e.target.value)} maxLength={50} disabled={!canEdit} />
                      <input placeholder="기사 제목" value={s.title ?? ''} onChange={(e) => upd('title', e.target.value)} maxLength={120} disabled={!canEdit} />
                      <input placeholder="2026.10.14" value={s.date ?? ''} onChange={(e) => upd('date', e.target.value)} maxLength={20} disabled={!canEdit} />
                      <input placeholder="https://" value={s.url ?? ''} onChange={(e) => upd('url', e.target.value)} maxLength={500} disabled={!canEdit} />
                      {canEdit && (
                        <button className="icon" aria-label="출처 빼기" onClick={() => set('sources', form.sources.filter((_, j) => j !== i))}>
                          ✕
                        </button>
                      )}
                    </div>
                  )
                })}
              </div>
            )}
          </section>

          <RelatedPicker form={form} setForm={setForm} canEdit={canEdit} initial={detail?.related ?? []} />
        </div>

        <aside className="ai-side">
          <section className="panel">
            <div className="panel-head">
              <h2>게시</h2>
              <Badge tone={AI_STATE_TONE[state]}>{AI_STATE[state]}</Badge>
            </div>
            {p && (
              <dl className="history">
                <dt>게시 일시</dt>
                <dd>{dateTime(p.publishAt)}</dd>
                {p.contentUpdatedAt && (
                  <>
                    <dt>본문 수정</dt>
                    <dd>{dateTime(p.contentUpdatedAt)}</dd>
                  </>
                )}
                <dt>조회</dt>
                <dd>{won(p.viewCount)}</dd>
                <dt>작성</dt>
                <dd>
                  {p.createdBy} · {dateTime(p.createdAt)}
                </dd>
                <dt>수정</dt>
                <dd>
                  {p.updatedBy} · {dateTime(p.updatedAt)}
                </dd>
              </dl>
            )}
            <div className="ai-publish">
              {canEdit && (
                <button className={detail ? '' : 'primary'} disabled={busy || (!!detail && !dirty)} onClick={save}>
                  {detail ? (dirty ? '변경 저장' : '저장됨') : '임시 저장'}
                </button>
              )}
              {detail && canApprove && (
                <>
                  {state !== 'PUBLISHED' && (
                    <button
                      className="primary"
                      disabled={busy || dirty}
                      title={dirty ? '먼저 저장해 주세요' : ''}
                      onClick={() =>
                        window.confirm('지금 게시합니다. 프런트 알뜰AI에 바로 보입니다.') &&
                        act(() => post(`/contents/posts/${detail.post.id}/publish`, { publishAt: null }), '게시했습니다')
                      }
                    >
                      즉시 게시
                    </button>
                  )}
                  {state !== 'PUBLISHED' && (
                    <button disabled={busy || dirty} onClick={() => setScheduling(true)}>
                      게시 예약 📅
                    </button>
                  )}
                  {(state === 'PUBLISHED' || state === 'SCHEDULED') && (
                    <button disabled={busy} onClick={() => act(() => post(`/contents/posts/${detail.post.id}/status`, { status: 'HIDDEN' }), '비노출로 바꿨습니다')}>
                      비노출
                    </button>
                  )}
                  {state === 'SCHEDULED' && (
                    <button disabled={busy} onClick={() => act(() => post(`/contents/posts/${detail.post.id}/status`, { status: 'DRAFT' }), '예약을 취소했습니다')}>
                      예약 취소
                    </button>
                  )}
                  {state === 'HIDDEN' && p?.publishAt && (
                    <button disabled={busy} onClick={() => act(() => post(`/contents/posts/${detail.post.id}/status`, { status: 'PUBLISHED' }), '다시 노출합니다')}>
                      다시 노출
                    </button>
                  )}
                </>
              )}
              {detail && canDelete && (
                <button
                  className="danger"
                  disabled={busy}
                  onClick={() =>
                    window.confirm('이 글을 삭제합니다. 되돌릴 수 없습니다. (잠시 내리려면 [비노출])') &&
                    run(() => del(`/contents/posts/${detail.post.id}`), () => '삭제했습니다').then(() => navigate('/contents/posts'))
                  }
                >
                  삭제
                </button>
              )}
            </div>
            {dirty && detail && <p className="hint">저장하지 않은 변경이 있습니다. 게시 · 예약은 저장한 뒤에 할 수 있습니다.</p>}
          </section>

          <section className="panel">
            <h2>목록 카드 미리보기</h2>
            <div className="ai-card-preview" style={{ background: category?.bgColor, borderColor: category?.accentColor }}>
              <div className="text">
                <h3>{form.title || '제목을 입력해 주세요'}</h3>
                <p className="meta">
                  {form.sponsored && <span className="ad">제휴</span>}
                  <b style={{ color: category?.accentColor }}>{category?.name}</b> · {p?.publishAt ? dateTime(p.publishAt).slice(0, 5) : 'MM.DD'} ·{' '}
                  {Math.max(1, Math.round(form.bodyMd.length / 500))}분 읽기
                </p>
              </div>
              <div className="thumb">{form.thumbnailUrl ? <img src={form.thumbnailUrl} alt="" /> : <span style={{ color: category?.accentColor }}>기본 그림</span>}</div>
            </div>
          </section>

          <section className="panel">
            <h2>썸네일</h2>
            <p className="muted small">가로 4 : 세로 3 이상, jpg · png · webp · gif, 5MB 이하. 카드 오른쪽에 아래가 살짝 잘려 보입니다. 없으면 카테고리 기본 그림을 씁니다.</p>
            {form.thumbnailUrl && <img className="ai-thumb-large" src={form.thumbnailUrl} alt="썸네일" />}
            {canEdit && (
              <div className="actions left">
                <button onClick={() => pickImage((url) => set('thumbnailUrl', url))}>{form.thumbnailUrl ? '바꾸기' : '이미지 올리기'}</button>
                {form.thumbnailUrl && <button onClick={() => set('thumbnailUrl', '')}>빼기</button>}
              </div>
            )}
          </section>

          <section className="panel">
            <h2>태그 · 노출</h2>
            <div className="form-grid one">
              <label>
                태그 <span className="muted small">쉼표로 구분, 6개까지</span>
                <input value={form.tags} onChange={(e) => set('tags', e.target.value)} placeholder="eSIM, 개통, 알뜰폰" disabled={!canEdit} />
              </label>
              <label className="inline">
                <input type="checkbox" checked={form.pinned} onChange={(e) => set('pinned', e.target.checked)} disabled={!canEdit} /> 카테고리 상단 고정 (카테고리당 1개 — 다른 글의 고정은 풀립니다)
              </label>
              <label className="inline">
                <input type="checkbox" checked={form.sponsored} onChange={(e) => set('sponsored', e.target.checked)} disabled={!canEdit} /> 제휴 콘텐츠 (카드 · 상세에 "제휴" 표시)
              </label>
            </div>
          </section>

          <section className="panel">
            <h2>검색 노출 (SEO)</h2>
            <div className="form-grid one">
              <label>
                검색용 제목 <span className="muted small">비우면 글 제목</span>
                <input value={form.seoTitle} onChange={(e) => set('seoTitle', e.target.value)} maxLength={120} disabled={!canEdit} />
              </label>
              <label>
                검색용 설명 <span className="muted small">비우면 카드 요약 ({form.seoDescription.length} / 200)</span>
                <textarea rows={3} value={form.seoDescription} onChange={(e) => set('seoDescription', e.target.value)} maxLength={200} disabled={!canEdit} />
              </label>
            </div>
          </section>
        </aside>
      </div>

      {scheduling && detail && (
        <Modal title="게시 예약" onClose={() => setScheduling(false)}>
          <div className="form-grid">
            <label>
              게시 일시 <input type="datetime-local" value={publishAt} onChange={(e) => setPublishAt(e.target.value)} />
            </label>
          </div>
          <div className="actions">
            <button onClick={() => setScheduling(false)}>취소</button>
            <button
              className="primary"
              disabled={busy || !publishAt}
              onClick={() =>
                act(() => post(`/contents/posts/${detail.post.id}/publish`, { publishAt: localToIso(publishAt) }), '게시를 예약했습니다').then(() =>
                  setScheduling(false),
                )
              }
            >
              예약
            </button>
          </div>
        </Modal>
      )}
    </>
  )
}

/** 관련 요금제 (3개) · 인터넷 상품 (2개): 지금 게시 · 노출 중인 상품에서 고른다 */
function RelatedPicker({
  form,
  setForm,
  canEdit,
  initial,
}: {
  form: Form
  setForm: (f: (cur: Form) => Form) => void
  canEdit: boolean
  initial: AiRelatedOption[]
}) {
  const [q, setQ] = useState('')
  const [open, setOpen] = useState(false)
  const options = useLoad(() => (open ? get<AiRelatedOption[]>('/contents/related-options') : Promise.resolve(null)), [open])
  const all = useMemo(() => {
    const m = new Map<string, AiRelatedOption>()
    for (const o of [...initial, ...(options.data ?? [])]) m.set(o.kind + o.id, o)
    return m
  }, [initial, options.data])
  const chosen = [
    ...form.relatedPlanIds.map((id) => all.get('PLAN' + id) ?? { id, kind: 'PLAN' as const, label: `요금제 #${id} (게시 중 아님)`, partner: null, price: null }),
    ...form.relatedInternetIds.map(
      (id) => all.get('INTERNET' + id) ?? { id, kind: 'INTERNET' as const, label: `인터넷 #${id} (노출 중 아님)`, partner: null, price: null },
    ),
  ]
  const lq = q.trim().toLowerCase()
  const list = (options.data ?? []).filter((o) => !lq || (o.label + ' ' + (o.partner ?? '')).toLowerCase().includes(lq)).slice(0, 40)
  const toggle = (o: AiRelatedOption) =>
    setForm((f) => {
      const key = o.kind === 'PLAN' ? 'relatedPlanIds' : 'relatedInternetIds'
      const cur = f[key]
      return { ...f, [key]: cur.includes(o.id) ? cur.filter((x) => x !== o.id) : [...cur, o.id] }
    })
  const isOn = (o: AiRelatedOption) => (o.kind === 'PLAN' ? form.relatedPlanIds : form.relatedInternetIds).includes(o.id)
  const full = (o: AiRelatedOption) => !isOn(o) && (o.kind === 'PLAN' ? form.relatedPlanIds.length >= 3 : form.relatedInternetIds.length >= 2)

  return (
    <section className="panel">
      <div className="panel-head">
        <h2>
          관련 상품 <span className="muted small">상세 화면 아래 "이 글과 관련된 요금제"로 보입니다 · 요금제 3개, 인터넷 2개까지</span>
        </h2>
        {canEdit && <button onClick={() => setOpen((o) => !o)}>{open ? '닫기' : '+ 고르기'}</button>}
      </div>
      {chosen.length === 0 ? (
        <p className="muted small">고른 상품이 없습니다.</p>
      ) : (
        <div className="chips">
          {chosen.map((o) => (
            <span key={o.kind + o.id} className="mini">
              {o.kind === 'PLAN' ? '요금제' : '인터넷'} · {o.partner ? o.partner + ' ' : ''}
              {o.label}
              {canEdit && (
                <button className="icon" aria-label="빼기" onClick={() => toggle(o)}>
                  ✕
                </button>
              )}
            </span>
          ))}
        </div>
      )}
      {open && (
        <div className="ai-picker">
          <input placeholder="요금제 · 제휴사 · 인터넷 상품 검색" value={q} onChange={(e) => setQ(e.target.value)} autoFocus />
          {!options.data ? (
            <Loading error={options.error} />
          ) : (
            <div className="pick-list">
              {list.map((o) => (
                <button key={o.kind + o.id} className={isOn(o) ? 'on' : ''} disabled={full(o)} onClick={() => toggle(o)}>
                  <span>
                    {isOn(o) ? '✓ ' : ''}
                    <Badge tone={o.kind === 'PLAN' ? 'coral' : 'navy'}>{o.kind === 'PLAN' ? '요금제' : '인터넷'}</Badge> {o.partner} · {o.label}
                  </span>
                  <span className="muted">{o.price != null ? won(o.price) + '원' : ''}</span>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </section>
  )
}

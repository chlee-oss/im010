import { useState } from 'react'
import { Loading, PageHead } from '../../components/ui'
import { get, put } from '../../lib/api'
import { fullDateTime } from '../../lib/format'
import { useMe } from '../../lib/me'
import { useRun } from '../../lib/notice'
import type { SeoSettings } from '../../lib/types'
import { useLoad } from '../../lib/useLoad'

type Form = Pick<SeoSettings, 'title' | 'description' | 'ogTitle' | 'ogDescription'>

const SITE_URL = 'https://www.im010.co.kr'
const OG_IMAGE = `${SITE_URL}/og-default.png`

/** 서버와 같은 정리: 줄바꿈 · 연속 공백 → 한 칸, 앞뒤 공백 제거 */
const clean = (s: string) => s.replace(/\s+/g, ' ').trim()
/** 글자 수 (한글 · 이모지 1자) */
const count = (s: string) => [...clean(s)].length

/** 권장 길이(제목 40 · 설명 max)를 넘으면 노랑, 최대를 넘으면 빨강 */
function Counter({ value, max, recommend }: { value: string; max: number; recommend: number }) {
  const n = count(value)
  const tone = n > max ? 'over' : n > recommend ? 'warn' : ''
  return (
    <span className={'seo-count ' + tone}>
      {n} / {max}자{n > max ? ' — 저장할 수 없습니다' : n > recommend ? ' — 검색 결과에서 잘릴 수 있습니다' : ''}
    </span>
  )
}

function Field({
  label,
  hint,
  value,
  max,
  recommend,
  multiline,
  disabled,
  placeholder,
  onChange,
}: {
  label: string
  hint: string
  value: string
  max: number
  recommend: number
  multiline?: boolean
  disabled: boolean
  placeholder?: string
  onChange: (v: string) => void
}) {
  return (
    <label className="seo-field">
      <span className="seo-label">
        {label}
        <Counter value={value} max={max} recommend={recommend} />
      </span>
      {multiline ? (
        <textarea rows={2} disabled={disabled} value={value} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} />
      ) : (
        <input disabled={disabled} value={value} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} />
      )}
      <small className="muted">{hint}</small>
    </label>
  )
}

/**
 * ST-08 검색엔진설정: 메인(/) · 기본 틀의 사이트 제목 · 설명 · Open Graph 제목 · 설명.
 * 저장하면 1분 안에 메인 HTML 에 반영된다 (im010-api). 요금제 · 알뜰AI 글 등 하위 페이지는 자기 제목 · 설명을 쓴다.
 */
export default function Seo() {
  const { can } = useMe()
  const { run, busy } = useRun()
  const { data, error, setData } = useLoad(() => get<SeoSettings>('/settings/seo'), [])
  const [form, setForm] = useState<Form | null>(null)

  if (!data) return <Loading error={error} />
  const { titleMax, descriptionMax } = data.limits
  const v: Form = form ?? data
  const set = (patch: Partial<Form>) => form && setForm({ ...form, ...patch })

  const title = clean(v.title) || data.defaultTitle
  const description = clean(v.description) || data.defaultDescription
  const ogTitle = clean(v.ogTitle) || title
  const ogDescription = clean(v.ogDescription) || description
  const invalid =
    !clean(v.title) || !clean(v.description) || count(v.title) > titleMax || count(v.ogTitle) > titleMax ||
    count(v.description) > descriptionMax || count(v.ogDescription) > descriptionMax || /[<>]/.test(Object.values(v).join(''))

  const save = () =>
    run(() => put<SeoSettings>('/settings/seo', form), () => '저장했습니다 — 1분 안에 메인 화면에 반영됩니다').then((r) => {
      if (r) {
        setData(r)
        setForm(null)
      }
    })

  return (
    <>
      <PageHead title="검색엔진설정">
        {can('ST-08', 'EDIT') && !form && (
          <button onClick={() => setForm({ title: data.title, description: data.description, ogTitle: data.ogTitle, ogDescription: data.ogDescription })}>
            수정
          </button>
        )}
      </PageHead>
      <p className="note">
        메인 화면(www.im010.co.kr)의 검색 결과 · 공유 미리보기 문구입니다. 저장하면 <b>1분 안에</b> 메인 화면에 반영되고, 매일 05:00 화면을 다시 만들 때도 이 값을 씁니다.
        <br />
        요금제 상세 · 알뜰AI 글 · 서비스 화면(/postpaid 등)은 각자의 제목 · 설명을 씁니다. 검색엔진에는 다음 수집 때(보통 며칠) 반영되고, 카카오톡은 미리보기를 캐시하므로{' '}
        <a href="https://developers.kakao.com/tool/debugger/sharing" target="_blank" rel="noreferrer">
          카카오 공유 디버거
        </a>
        에서 캐시를 지워야 바로 바뀝니다.
      </p>

      <div className="seo-layout">
        <section className="panel">
          <h2>검색 결과</h2>
          <Field
            label="사이트 제목"
            hint="브라우저 탭 · 검색 결과 제목 (<title>)"
            value={v.title}
            max={titleMax}
            recommend={40}
            disabled={!form}
            onChange={(t) => set({ title: t })}
          />
          <Field
            label="사이트 설명"
            hint="검색 결과 제목 아래 설명 (meta description)"
            value={v.description}
            max={descriptionMax}
            recommend={descriptionMax}
            multiline
            disabled={!form}
            onChange={(t) => set({ description: t })}
          />
          <h2 className="seo-sub">공유 미리보기 (Open Graph)</h2>
          <Field
            label="Open Graph 제목"
            hint="카카오톡 · 페이스북 등 공유 카드 제목 (og:title). 비워 두면 사이트 제목"
            value={v.ogTitle}
            max={titleMax}
            recommend={40}
            disabled={!form}
            placeholder={form ? title : '(사이트 제목과 같음)'}
            onChange={(t) => set({ ogTitle: t })}
          />
          <Field
            label="Open Graph 설명"
            hint="공유 카드 설명 (og:description). 비워 두면 사이트 설명"
            value={v.ogDescription}
            max={descriptionMax}
            recommend={descriptionMax}
            multiline
            disabled={!form}
            placeholder={form ? description : '(사이트 설명과 같음)'}
            onChange={(t) => set({ ogDescription: t })}
          />
          {form && (
            <div className="actions">
              <button
                type="button"
                className="seo-reset"
                onClick={() => setForm({ title: data.defaultTitle, description: data.defaultDescription, ogTitle: '', ogDescription: '' })}
              >
                기본값으로 채우기
              </button>
              <button onClick={() => setForm(null)}>취소</button>
              <button className="primary" disabled={busy || invalid} onClick={() => void save()}>
                저장
              </button>
            </div>
          )}
          <p className="muted seo-updated">
            마지막 저장 {data.updatedAt ? `${fullDateTime(data.updatedAt)} · ${data.updatedBy}` : '없음 (초기값)'} · 변경 내용은 접속이력관리에 남습니다
          </p>
        </section>

        <section className="panel seo-preview">
          <h2>미리보기</h2>
          <h3>검색 결과</h3>
          <div className="serp">
            <div className="serp-site">im010 · {SITE_URL.replace('https://', '')}</div>
            <div className="serp-title">{title}</div>
            <div className="serp-desc">{description}</div>
          </div>
          <h3>카카오톡 · SNS 공유</h3>
          <div className="og-card">
            <img src={OG_IMAGE} alt="" />
            <div className="og-body">
              <div className="og-title">{ogTitle}</div>
              <div className="og-desc">{ogDescription}</div>
              <div className="og-url">www.im010.co.kr</div>
            </div>
          </div>
        </section>
      </div>
    </>
  )
}

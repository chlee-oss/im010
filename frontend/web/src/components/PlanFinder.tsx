import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import type { PlanSummary } from '../api/types'
import { won } from '../lib/format'
import { BrandChip, usePartners } from '../lib/partners'
import { pickTwo } from '../lib/planPicks'
import { FILTER_GROUPS, QUICK_FILTERS, SORTS, matches, type FilterGroup, type Selection, type SortKey } from '../lib/planFilters'
import { track } from '../lib/track'
import { useApi } from '../lib/useApi'
import { PlanListCard } from './PlanListCard'
import StateMessage from './StateMessage'
import Icon from './Icon'

const PAGE = 12

/**
 * 조건으로 찾고, 제휴사별로 비교해요 — 예전 "내가 찾는 요금제를 직접 찾아요"(계산기) +
 * "입점한 제휴사에서 소개해요"(제휴사 탭)를 하나로 합친 영역.
 *   1줄: 제휴사 탭 (여러 곳 고르기)
 *   2줄: 조건 버튼 (누르면 그 조건 칩이 열리는 작은 창 · 모바일은 아래에서 올라오는 시트) + 전체 초기화
 *   결과: 개수 · 고른 조건 태그 · 보기(제휴사별/목록) · 정렬 → 이 조건 최저가 한 줄 → 카드
 */
/** heading: 섹션 제목을 바꿀 때 (/postpaid 는 큰 제목과 겹치지 않게 "조건 고르기") */
export function PlanFinder({ heading }: { heading?: string } = {}) {
  const partners = usePartners()
  const { data, loading, error } = useApi((signal) => api.plans('POSTPAID', undefined, signal), [])
  const all = useMemo(() => data ?? [], [data])

  const [partnerSel, setPartnerSel] = useState<string[]>(() => {
    // 헤더 드롭다운에서 브랜드를 고르고 들어온 경우
    const picked = sessionStorage.getItem('im010.partnerTab')
    sessionStorage.removeItem('im010.partnerTab')
    return picked && picked !== 'all' ? [picked] : []
  })
  const [sel, setSel] = useState<Selection>({})
  const [open, setOpen] = useState<string | null>(null) // 열린 조건 창: 그룹 key 또는 'all'(모바일 전체 조건)
  const [view, setView] = useState<'group' | 'list'>('group')
  const [sort, setSort] = useState<SortKey>('price')
  const [limit, setLimit] = useState(PAGE)
  const [expanded, setExpanded] = useState<string[]>([])
  const barRef = useRef<HTMLDivElement>(null)
  // 마우스를 쓰는 화면(PC): 조건 버튼에 커서를 올리면 조건 창이 열리고, 벗어나면 닫힌다
  const canHover = useMemo(() => typeof window !== 'undefined' && window.matchMedia('(hover: hover) and (pointer: fine)').matches, [])
  const hoverTimer = useRef<number | undefined>(undefined)
  const hoverOpen = (key: string) => {
    if (!canHover) return
    window.clearTimeout(hoverTimer.current)
    // 다른 조건 창이 열려 있으면 바로 바꾸고, 처음 열 때만 잠깐 기다린다 (지나가다 스친 경우 열리지 않게)
    if (open && open !== 'all') setOpen(key)
    else hoverTimer.current = window.setTimeout(() => setOpen(key), 150)
  }
  const hoverClose = (key: string) => {
    if (!canHover) return
    window.clearTimeout(hoverTimer.current)
    // 버튼과 창 사이 틈을 지나는 동안 닫히지 않게 조금 기다린다
    hoverTimer.current = window.setTimeout(() => setOpen((cur) => (cur === key ? null : cur)), 250)
  }
  useEffect(() => () => window.clearTimeout(hoverTimer.current), [])

  // 제휴사도 조건 그룹 하나로 다룬다 (칩 숫자 · 태그 · 초기화를 같은 방식으로)
  const partnerGroup: FilterGroup = useMemo(
    () => ({
      key: 'partner',
      label: '입점사',
      opts: [...partners.values()].map((pt) => ({ v: pt.code, t: pt.name, test: (p: PlanSummary) => p.partnerCode === pt.code })),
    }),
    [partners],
  )
  const groups = useMemo(() => [partnerGroup, ...FILTER_GROUPS], [partnerGroup])
  const selection: Selection = useMemo(() => ({ ...sel, partner: partnerSel }), [sel, partnerSel])
  const picked = groups.reduce((n, g) => n + (selection[g.key]?.length ?? 0), 0)
  const pickedConditions = picked - partnerSel.length

  const list = useMemo(() => all.filter((p) => matches(p, groups, selection)).sort(SORTS[sort].cmp), [all, groups, selection, sort])
  const count = (g: FilterGroup, v: string) => {
    const o = g.opts.find((o) => o.v === v)!
    return all.filter((p) => matches(p, groups, selection, g.key) && o.test(p)).length
  }

  // 조건 창 밖을 누르거나 Esc 를 누르면 닫는다
  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (open !== 'all' && barRef.current && !barRef.current.contains(e.target as Node)) setOpen(null)
    }
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(null)
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const changed = () => {
    setLimit(PAGE)
    setExpanded([])
  }
  const toggle = (g: FilterGroup, v: string) => {
    const cur = selection[g.key] ?? []
    const next = cur.includes(v) ? cur.filter((x) => x !== v) : g.single ? [v] : [...cur, v]
    if (g.key === 'partner') setPartnerSel(next)
    else setSel((s) => ({ ...s, [g.key]: next }))
    if (!cur.includes(v)) track('filter', { props: { dim: `finder_${g.key}`, value: v } })
    changed()
  }
  const clearGroup = (key: string) => {
    if (key === 'partner') setPartnerSel([])
    else setSel((s) => ({ ...s, [key]: [] }))
    changed()
  }
  const resetAll = () => {
    setPartnerSel([])
    setSel({})
    changed()
  }

  /** 조건 버튼 글자: 하나 골랐으면 그 값, 여러 개면 "데이터 2" */
  const buttonText = (g: FilterGroup) => {
    const s = selection[g.key] ?? []
    if (s.length === 0) return g.label
    if (s.length === 1) return g.opts.find((o) => o.v === s[0])?.t ?? g.label
    return `${g.label} ${s.length}`
  }

  /** closeAfter: 조건 창에서는 하나 고르면 창을 닫는다 (모바일 전체 조건 시트는 열어 둔다) */
  const chips = (g: FilterGroup, closeAfter = false) => (
    <div className="pf-chips">
      {g.opts.map((o) => {
        const on = (selection[g.key] ?? []).includes(o.v)
        const n = count(g, o.v)
        return (
          <button
            key={o.v}
            type="button"
            className={`pf-chip${on ? ' on' : ''}`}
            aria-pressed={on}
            disabled={!on && n === 0}
            onClick={() => {
              toggle(g, o.v)
              if (closeAfter) {
                window.clearTimeout(hoverTimer.current)
                setOpen(null)
              }
            }}
          >
            {on && <Icon name="check" />}
            {o.t}
            <span className="pf-n">{n}</span>
          </button>
        )
      })}
    </div>
  )

  const best = picked > 0 && list.length > 0 ? [...list].sort(SORTS.price.cmp)[0] : null
  const partnerGroups = [...partners.values()]
    .map((pt) => {
      const own = list.filter((p) => p.partnerCode === pt.code)
      const isOpen = expanded.includes(pt.code)
      // 아무 조건도 없을 때는 지금 메인처럼 백오피스 지정 2개, 조건이 있으면 정렬 순 앞의 2개
      const head = picked === 0 && sort === 'price' ? pickTwo(own) : own.slice(0, 2)
      return { partner: pt, total: own.length, plans: isOpen ? own : head, isOpen }
    })
    .filter((g) => g.total > 0)

  return (
    <section id="partners" className="pf-band">
      <span id="calc-section" className="pf-anchor" aria-hidden="true" />
      <div className="wrap">
        <div className="sec-head">
          <div>
            <h2>{heading ?? '조건으로 찾고, 제휴사별로 비교해요'}</h2>
            <div className="sub">
              {partners.size || 4}개 제휴사 후불 요금제를 원하는 조건으로 골라 보세요
            </div>
          </div>
        </div>

        {/* 1줄: 제휴사 */}
        <div className="pf-partners" role="group" aria-label="제휴사">
          <button type="button" className={partnerSel.length === 0 ? 'active' : ''} onClick={() => clearGroup('partner')}>
            전체
          </button>
          {partnerGroup.opts.map((o) => (
            <button key={o.v} type="button" className={partnerSel.includes(o.v) ? 'active' : ''} aria-pressed={partnerSel.includes(o.v)} disabled={!partnerSel.includes(o.v) && count(partnerGroup, o.v) === 0} onClick={() => toggle(partnerGroup, o.v)}>
              {o.t}
              <span className="pf-n">{count(partnerGroup, o.v)}</span>
            </button>
          ))}
        </div>

        {/* 2줄: 조건 버튼 + 전체 초기화 */}
        <div className="pf-bar" ref={barRef}>
          <button type="button" className={`pf-btn pf-all${pickedConditions ? ' set' : ''}`} onClick={() => setOpen(open === 'all' ? null : 'all')}>
            <Icon name="menu" /> 조건{pickedConditions ? ` ${pickedConditions}` : ''}
          </button>
          {FILTER_GROUPS.map((g) => {
            const set = (selection[g.key] ?? []).length > 0
            return (
              <div key={g.key} className="pf-drop" onMouseEnter={() => hoverOpen(g.key)} onMouseLeave={() => hoverClose(g.key)}>
                <button
                  type="button"
                  className={`pf-btn${set ? ' set' : ''}${open === g.key ? ' open' : ''}`}
                  aria-expanded={open === g.key}
                  onClick={() => {
                    window.clearTimeout(hoverTimer.current)
                    // 마우스 화면에서는 이미 커서로 열려 있으므로 누르면 열린 채로 둔다 (키보드 · 터치는 누를 때마다 열고 닫기)
                    setOpen(open === g.key && !canHover ? null : g.key)
                  }}
                >
                  {buttonText(g)} <Icon name="expand_more" />
                </button>
                {open === g.key && (
                  <div className="pf-pop" role="dialog" aria-label={g.label}>
                    <div className="pf-pop-head">
                      <b>{g.label}</b>
                      {g.hint && <span>{g.hint}</span>}
                      <button type="button" className="pf-x" aria-label="닫기" onClick={() => setOpen(null)}>
                        <Icon name="close" />
                      </button>
                    </div>
                    {chips(g, true)}
                    <div className="pf-pop-foot">
                      <button type="button" onClick={() => clearGroup(g.key)} disabled={!set}>
                        이 조건 지우기
                      </button>
                      <button type="button" className="primary" onClick={() => setOpen(null)}>
                        요금제 {list.length}개 보기
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )
          })}
          <button type="button" className="pf-reset" onClick={resetAll} disabled={picked === 0}>
            <Icon name="restart_alt" /> 전체 초기화
          </button>
        </div>

        {/* 자주 찾는 조건 */}
        <div className="pf-quick" aria-label="자주 찾는 조건">
          <span>자주 찾는 조건</span>
          {QUICK_FILTERS.map((q) => {
            const g = FILTER_GROUPS.find((g) => g.key === q.key)!
            const on = (selection[q.key] ?? []).includes(q.v)
            return (
              <button key={q.t} type="button" className={on ? 'on' : ''} aria-pressed={on} onClick={() => toggle(g, q.v)}>
                {q.t}
              </button>
            )
          })}
        </div>

        {/* 모바일: 전체 조건 시트 */}
        {open === 'all' && (
          <>
            <div className="pf-dim" onClick={() => setOpen(null)} />
            <div className="pf-sheet" role="dialog" aria-label="조건 고르기">
              <div className="pf-pop-head">
                <b>조건 고르기</b>
                <button type="button" className="pf-x" aria-label="닫기" onClick={() => setOpen(null)}>
                  <Icon name="close" />
                </button>
              </div>
              {FILTER_GROUPS.map((g) => (
                <div key={g.key} className="pf-sheet-row">
                  <p>
                    {g.label} {g.hint && <span>{g.hint}</span>}
                  </p>
                  {chips(g)}
                </div>
              ))}
              <div className="pf-pop-foot">
                <button type="button" onClick={resetAll} disabled={picked === 0}>
                  전체 초기화
                </button>
                <button type="button" className="primary" onClick={() => setOpen(null)}>
                  요금제 {list.length}개 보기
                </button>
              </div>
            </div>
          </>
        )}

        {/* 결과 막대 */}
        <div className="pf-result">
          <p className="pf-count" aria-live="polite">
            요금제 <b>{list.length}</b>개
          </p>
          <div className="pf-tags">
            {groups.flatMap((g) =>
              (selection[g.key] ?? []).map((v) => (
                <button key={g.key + v} type="button" className="pf-tag" onClick={() => toggle(g, v)} aria-label={`${g.label} ${v} 지우기`}>
                  {g.opts.find((o) => o.v === v)?.t}
                  <Icon name="close" />
                </button>
              )),
            )}
          </div>
          <div className="pf-tools">
            <div className="pf-seg" role="group" aria-label="보기">
              <button type="button" className={view === 'group' ? 'on' : ''} onClick={() => setView('group')}>
                제휴사별
              </button>
              <button type="button" className={view === 'list' ? 'on' : ''} onClick={() => setView('list')}>
                목록
              </button>
            </div>
            <select value={sort} onChange={(e) => setSort(e.target.value as SortKey)} aria-label="정렬">
              {Object.entries(SORTS).map(([k, s]) => (
                <option key={k} value={k}>
                  {s.label}
                </option>
              ))}
            </select>
          </div>
        </div>

        <StateMessage loading={loading && !data} error={error} empty={false} />

        {data && list.length === 0 && (
          <div className="pf-empty">
            <p>조건에 맞는 요금제가 없어요. 조건을 하나씩 빼 보세요.</p>
            <button type="button" onClick={resetAll}>
              전체 초기화
            </button>
          </div>
        )}

        {/* 이 조건의 최저가 (예전 계산기 결과 자리) */}
        {best && (
          <Link className="pf-best" to={`/plans/${best.id}`}>
            <span className="k">이 조건 최저가</span>
            <span className="nm">
              {best.partnerName} · {best.name}
            </span>
            <span className="pr num">
              월 {won(best.monthlyPrice)}원
              {best.discountMonths && best.priceAfterDiscount != null && (
                <small>
                  {' '}
                  · {best.discountMonths}개월 이후 {won(best.priceAfterDiscount)}원
                </small>
              )}
            </span>
            <Icon name="chevron_right" />
          </Link>
        )}

        {view === 'group'
          ? partnerGroups.map((g) => (
              <div className="lp-group" key={g.partner.code}>
                <div className="lp-head">
                  <BrandChip code={g.partner.code} name={g.partner.name} />
                  {g.total > 2 && (
                    <button
                      type="button"
                      className="lp-all"
                      onClick={() => setExpanded((x) => (g.isOpen ? x.filter((c) => c !== g.partner.code) : [...x, g.partner.code]))}
                    >
                      {g.isOpen ? '접기' : `${g.partner.name} ${g.total}개 모두 보기`} <Icon name={g.isOpen ? 'expand_more' : 'chevron_right'} />
                    </button>
                  )}
                </div>
                <div className="lp-pair">
                  {g.plans.map((p) => (
                    <PlanListCard key={p.id} plan={p} />
                  ))}
                </div>
              </div>
            ))
          : list.length > 0 && (
              <>
                <div className="lp-pair">
                  {list.slice(0, limit).map((p) => (
                    <PlanListCard key={p.id} plan={p} />
                  ))}
                </div>
                {list.length > limit && (
                  <button type="button" className="pf-more" onClick={() => setLimit((n) => n + PAGE)}>
                    더 보기 <span>({list.length - limit}개 남음)</span>
                  </button>
                )}
              </>
            )}

        <p className="data-basis">
          ※ 각 제휴사 공시 요금(VAT 포함). 개통 조건은 요금제 상세의 "개통하기"로 이동한 제휴사 페이지에서 확인해 주세요.
        </p>
      </div>
    </section>
  )
}

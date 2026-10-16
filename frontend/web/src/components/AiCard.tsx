import type { CSSProperties } from 'react'
import { Link } from 'react-router-dom'
import type { AiCard as Card } from '../api/types'
import { aiPostPath, aiShortDate } from '../lib/aiFormat'

/** 썸네일이 없을 때: 카테고리 강조 색으로 그린 기본 그림 (화면 미리보기 느낌) */
export function AiDefaultThumb({ slug, accent }: { slug: string; accent: string }) {
  if (slug === 'internet') {
    return (
      <svg viewBox="0 0 120 92" aria-hidden="true">
        <rect width="120" height="92" fill="#fff" />
        <rect width="120" height="14" fill="#F4F5F7" />
        <circle cx="8" cy="7" r="2.5" fill="#E2E4E9" />
        <circle cx="16" cy="7" r="2.5" fill="#E2E4E9" />
        <path d="M36 48a34 34 0 0 1 48 0" stroke={accent} strokeWidth="6" fill="none" strokeLinecap="round" />
        <path d="M46 58a20 20 0 0 1 28 0" stroke={accent} strokeWidth="6" fill="none" strokeLinecap="round" opacity=".7" />
        <circle cx="60" cy="70" r="5" fill={accent} />
      </svg>
    )
  }
  if (slug === 'mobile') {
    return (
      <svg viewBox="0 0 120 92" aria-hidden="true">
        <rect width="120" height="92" fill="#fff" />
        <rect x="40" y="10" width="40" height="76" rx="8" fill="#1F2430" />
        <rect x="44" y="18" width="32" height="56" rx="3" fill={accent} opacity=".85" />
        <rect x="50" y="30" width="20" height="5" rx="2.5" fill="#fff" />
        <rect x="50" y="40" width="14" height="5" rx="2.5" fill="#fff" opacity=".7" />
        <rect x="50" y="56" width="20" height="10" rx="5" fill="#fff" />
      </svg>
    )
  }
  return (
    <svg viewBox="0 0 120 92" aria-hidden="true">
      <rect width="120" height="92" fill="#fff" />
      <circle cx="60" cy="46" r="26" fill={accent} opacity=".2" />
      <path d="M60 24l5 15 15 5-15 5-5 15-5-15-15-5 15-5z" fill={accent} />
      <circle cx="92" cy="22" r="5" fill={accent} opacity=".6" />
      <circle cx="26" cy="70" r="4" fill={accent} opacity=".5" />
    </svg>
  )
}

function Thumb({ card }: { card: Card }) {
  return (
    <div className="ai-thumb">
      {card.thumbnailUrl ? (
        <img src={card.thumbnailUrl} alt="" loading="lazy" decoding="async" />
      ) : (
        <AiDefaultThumb slug={card.categorySlug} accent={card.accentColor} />
      )}
    </div>
  )
}

const colors = (c: Card) => ({ '--ai-bg': c.bgColor, '--ai-ac': c.accentColor }) as CSSProperties

/**
 * 알뜰AI 목록 카드 (목업 ①): 파스텔 배경 · 제목 왼쪽 두 줄 · 썸네일 오른쪽(아래가 잘림) · 선택 시 강조 테두리.
 * current = 지금 보고 있는 글.
 */
export function AiCard({ card, current }: { card: Card; current?: boolean }) {
  const date = aiShortDate(card.publishedAt)
  return (
    <Link
      className={'ai-card' + (current ? ' current' : '')}
      style={colors(card)}
      to={aiPostPath(card)}
      aria-current={current ? 'page' : undefined}
      aria-label={`${card.title}, ${card.categoryName}, ${date}${card.sponsored ? ', 제휴' : ''}`}
    >
      <div className="ai-text">
        <h3>
          {card.title}
          {card.isNew && <span className="ai-new">N</span>}
        </h3>
        <p className="ai-meta">
          {card.sponsored && <span className="ai-ad">제휴</span>}
          <b>{card.categoryName}</b> · {date} · {card.readMinutes}분 읽기
        </p>
      </div>
      <Thumb card={card} />
    </Link>
  )
}

/** 상단 고정 글: 큰 카드 (요약 문장 포함) */
export function AiFeaturedCard({ card }: { card: Card }) {
  return (
    <Link className="ai-card ai-featured" style={colors(card)} to={aiPostPath(card)}>
      <div className="ai-text">
        <div>
          <span className="ai-pin">📌 추천</span>
          <h3>{card.title}</h3>
          {card.cardSummary && <p className="ai-sum">{card.cardSummary}</p>}
        </div>
        <p className="ai-meta">
          {card.sponsored && <span className="ai-ad">제휴</span>}
          <b>{card.categoryName}</b> · {aiShortDate(card.publishedAt)} · {card.readMinutes}분 읽기
        </p>
      </div>
      <Thumb card={card} />
    </Link>
  )
}

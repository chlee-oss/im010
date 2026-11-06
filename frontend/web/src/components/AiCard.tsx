import type { CSSProperties } from 'react'
import { Link } from 'react-router-dom'
import type { AiCard as Card } from '../api/types'
import { aiPostPath, aiShortDate } from '../lib/aiFormat'
import Icon from './Icon'

/** 썸네일이 없을 때: 카테고리에 맞는 Material Symbols 아이콘을 강조 색으로 (알뜰통신 · 인터넷 · 잡식잡식) */
const THUMB_ICON: Record<string, string> = { mobile: 'smartphone', internet: 'router', misc: 'lightbulb' }

export function AiDefaultThumb({ slug, accent }: { slug: string; accent: string }) {
  return (
    <div className="ai-default-thumb" style={{ color: accent }}>
      <Icon name={THUMB_ICON[slug] ?? 'article'} />
    </div>
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

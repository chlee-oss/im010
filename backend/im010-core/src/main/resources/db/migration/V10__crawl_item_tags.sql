-- 수집 건 혜택 태그: 제휴사 카드의 배지 (5G · 무제한 ...). 승인할 때 요금제 버전의 혜택 태그로 쓴다.
-- 변경 감지(value_hash)에는 넣지 않는다 — 배지만 바뀐 것으로 점검 대기를 만들지 않는다.
ALTER TABLE crawl_item
    ADD COLUMN tags TEXT[] NOT NULL DEFAULT '{}';

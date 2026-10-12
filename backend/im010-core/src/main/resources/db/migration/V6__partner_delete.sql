-- 제휴사 삭제 (PA-01): 요금제 · 수집 이력이 없으면 행을 지우고, 있으면 이력을 남긴 채 삭제 표시만 한다.
-- 삭제 표시된 제휴사는 백오피스 목록 · 수집 · 프런트에서 빠지며 코드는 다시 쓸 수 없다.
ALTER TABLE partner
    ADD COLUMN deleted_at  TIMESTAMPTZ,
    ADD COLUMN deleted_by  VARCHAR(50);

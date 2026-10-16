-- 메인 "제휴사별 요금제" (im010_요금제리스트_목업.html): 제휴사마다 먼저 보여 줄 후불 요금제 1 · 2번째 (PR-01 에서 지정).
-- 지정하지 않은 자리는 게시 중인 요금제 중 월 요금이 낮은 순으로 채운다.
ALTER TABLE plan ADD COLUMN main_pick SMALLINT CHECK (main_pick IN (1, 2));
CREATE UNIQUE INDEX ux_plan_main_pick ON plan (partner_code, main_pick) WHERE main_pick IS NOT NULL;

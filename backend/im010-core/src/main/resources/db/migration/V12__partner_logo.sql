-- 제휴사 로고 (제휴사관리에서 업로드). /uploads/partners/... 경로. 없으면 화면은 브랜드 칩(chip_bg · chip_fg)을 쓴다.
ALTER TABLE partner ADD COLUMN logo_url VARCHAR(300);

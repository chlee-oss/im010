-- 알림 채널: 카카오워크 (봇) · 매일 08:00 정기 현황 리포트
--
-- 카카오워크 봇은 사람이 만든 채팅방에는 보낼 수 없고, 봇이 만든 채팅방(conversations.open)에만 보낸다.
-- 그래서 KAKAOWORK 채널은
--   webhook_url     = 봇 App Key (비밀값 — 화면에는 가려서 보여 준다)
--   recipients      = 채팅방에 넣을 멤버의 카카오워크 인증 이메일 (줄바꿈으로 구분)
--   conversation_id = 백오피스 [채팅방 만들기]로 봇이 만든 그룹 채팅방 ID (채널 이름 = 채팅방 이름)
-- 다른 메신저(SLACK · TEAMS · JANDI · WEBHOOK)는 recipients · conversation_id 를 쓰지 않는다.

ALTER TABLE notify_channel DROP CONSTRAINT notify_channel_kind_check;
ALTER TABLE notify_channel ADD CONSTRAINT notify_channel_kind_check
    CHECK (kind IN ('SLACK', 'TEAMS', 'JANDI', 'WEBHOOK', 'KAKAOWORK'));

ALTER TABLE notify_channel
    ADD COLUMN recipients      TEXT,
    ADD COLUMN conversation_id VARCHAR(50);

-- 정기 현황 리포트: 직전 리포트의 숫자 (전날 대비 증감 계산용, JSON)
INSERT INTO admin_setting (key, value) VALUES ('daily_report_counts', '')
ON CONFLICT (key) DO NOTHING;

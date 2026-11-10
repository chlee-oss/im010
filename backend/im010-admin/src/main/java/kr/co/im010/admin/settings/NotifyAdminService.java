package kr.co.im010.admin.settings;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.im010.admin.audit.AuditService;
import kr.co.im010.admin.auth.CurrentAdmin;
import kr.co.im010.admin.web.ApiException;
import kr.co.im010.admin.web.Texts;
import kr.co.im010.core.mapper.NotifyMapper;
import kr.co.im010.core.notify.AlertType;
import kr.co.im010.core.notify.DailyReport;
import kr.co.im010.core.notify.KakaoWorkClient;
import kr.co.im010.core.notify.WebhookSender;
import kr.co.im010.core.row.ChannelRow;
import kr.co.im010.core.row.MailRecipientRow;
import kr.co.im010.core.row.NotificationRow;

/**
 * 알림 설정 (ST-02): 메신저 채널(웹훅) 관리 · 테스트 발송 · 알림 이력, 관리자 본인의 메일 수신 설정.
 * 웹훅 주소는 비밀값이라 화면에는 앞부분만 보여 주고, 채널 관리는 최고관리자만 한다.
 */
@Service
public class NotifyAdminService {

    static final String PROGRAM = "ST-02";
    private static final Set<String> KINDS = Set.of("SLACK", "TEAMS", "JANDI", "WEBHOOK", "KAKAOWORK");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    /** 카카오워크 채팅방 멤버 수 상한 (알림방 용도) */
    private static final int MAX_RECIPIENTS = 30;

    public record AlertTypeDto(String code, String label, boolean messengerDefault) {
    }

    /**
     * webhookUrl 은 가린 값 (예: https://hooks.slack.com/serv…, 카카오워크는 봇 App Key 앞 4자리…).
     * 카카오워크: recipients = 채팅방 멤버 이메일, hasRoom = 봇이 만든 채팅방이 연결돼 있는지
     */
    public record ChannelDto(long id, String name, String kind, String webhookUrl, List<String> alertTypes, boolean enabled,
                             String updatedBy, OffsetDateTime updatedAt, List<String> recipients, boolean hasRoom) {
    }

    /** 카카오워크는 webhookUrl = 봇 App Key, recipients = 멤버 이메일 (줄바꿈 · 쉼표로 구분) */
    public record ChannelRequest(String name, String kind, String webhookUrl, List<String> alertTypes, boolean enabled,
                                 String recipients) {
    }

    public record MyNotify(String email, List<String> mailAlerts, List<AlertTypeDto> types) {
    }

    public record HistoryPage(int total, int page, List<NotificationRow> items) {
    }

    private final NotifyMapper notifyMapper;
    private final WebhookSender webhook;
    private final KakaoWorkClient kakaoWork;
    private final DailyReport dailyReport;
    private final AuditService audit;

    public NotifyAdminService(NotifyMapper notifyMapper, WebhookSender webhook, KakaoWorkClient kakaoWork,
                              DailyReport dailyReport, AuditService audit) {
        this.notifyMapper = notifyMapper;
        this.webhook = webhook;
        this.kakaoWork = kakaoWork;
        this.dailyReport = dailyReport;
        this.audit = audit;
    }

    /**
     * 정기 현황 리포트를 지금 만들어 알림함에 넣는다 → 배치가 "정기 현황 리포트"를 받는 채널 · 메일로 보낸다.
     * 매일 08:00 리포트의 전날 대비 증감 기준(직전 숫자)은 바꾸지 않는다.
     */
    @Transactional
    public String sendDailyReportNow() {
        CurrentAdmin.requireSuper();
        ZoneId kst = ZoneId.of("Asia/Seoul");
        DailyReport.Report r = dailyReport.build(LocalDate.now(kst), kst);
        notifyMapper.insertNotification(AlertType.DAILY_REPORT.name(), r.level(), r.title() + " (수동)", r.body(), null);
        audit.action(PROGRAM, "NOTIFY_DAILY_REPORT", r.title(), null);
        return "리포트를 알림함에 넣었습니다 — 30초 안에 \"정기 현황 리포트\"를 받는 채널로 발송됩니다";
    }

    public static List<AlertTypeDto> types() {
        return Arrays.stream(AlertType.values()).map(t -> new AlertTypeDto(t.name(), t.label(), t.messengerDefault())).toList();
    }

    public List<ChannelDto> channels() {
        return notifyMapper.findChannels().stream().map(NotifyAdminService::dto).toList();
    }

    @Transactional
    public List<ChannelDto> createChannel(ChannelRequest req) {
        CurrentAdmin.requireSuper();
        ChannelRequest r = validate(req, null);
        notifyMapper.insertChannel(r.name(), r.kind(), r.webhookUrl(), r.alertTypes().toArray(String[]::new), r.enabled(),
                r.recipients(), CurrentAdmin.get().loginId());
        audit.action(PROGRAM, "NOTIFY_CHANNEL_CREATE", r.name(), r.kind());
        return channels();
    }

    /**
     * 웹훅 주소(카카오워크는 App Key)를 비워 보내면 기존 값을 그대로 둔다 (화면에는 가린 값만 있으므로).
     * 메신저 종류나 카카오워크 봇(App Key)이 바뀌면 예전 채팅방은 쓸 수 없으므로 채팅방 연결을 지운다.
     */
    @Transactional
    public List<ChannelDto> updateChannel(long id, ChannelRequest req) {
        CurrentAdmin.requireSuper();
        ChannelRow current = find(id);
        ChannelRequest r = validate(req, current.kind().equals(req.kind()) ? current.webhookUrl() : null);
        notifyMapper.updateChannel(id, r.name(), r.kind(), r.webhookUrl(), r.alertTypes().toArray(String[]::new), r.enabled(),
                r.recipients(), CurrentAdmin.get().loginId());
        if (current.conversationId() != null
                && (!r.kind().equals("KAKAOWORK") || !r.webhookUrl().equals(current.webhookUrl()))) {
            notifyMapper.updateChannelConversation(id, null);
        }
        audit.action(PROGRAM, "NOTIFY_CHANNEL_UPDATE", r.name(), (r.enabled() ? "" : "끔 ") + String.join(",", r.alertTypes()));
        return channels();
    }

    @Transactional
    public List<ChannelDto> deleteChannel(long id) {
        CurrentAdmin.requireSuper();
        ChannelRow c = find(id);
        notifyMapper.deleteChannel(id);
        audit.action(PROGRAM, "NOTIFY_CHANNEL_DELETE", c.name(), c.kind());
        return channels();
    }

    /** 채널로 바로 테스트 메시지를 보낸다 (알림함을 거치지 않음). 실패 이유를 그대로 돌려준다. */
    public String testChannel(long id) {
        CurrentAdmin.requireSuper();
        ChannelRow c = find(id);
        String body = CurrentAdmin.get().name() + " 님이 백오피스에서 보낸 테스트 메시지입니다.";
        if (c.kind().equals("KAKAOWORK") && c.conversationId() == null) {
            throw ApiException.badRequest("먼저 [채팅방 만들기]로 봇 채팅방을 만들어 주세요");
        }
        try {
            if (c.kind().equals("KAKAOWORK")) {
                kakaoWork.send(c.webhookUrl(), c.conversationId(), KakaoWorkClient.text("INFO", "테스트", "알림 채널 연결 확인", body));
            } else {
                webhook.send(c.kind(), c.webhookUrl(), "INFO", "테스트", "알림 채널 연결 확인", body);
            }
            audit.action(PROGRAM, "NOTIFY_CHANNEL_TEST", c.name(), "OK");
            return "보냈습니다. 메신저에서 확인해 주세요";
        } catch (IOException e) {
            audit.action(PROGRAM, "NOTIFY_CHANNEL_TEST", c.name(), "FAIL " + e.getMessage());
            throw ApiException.badRequest("보내지 못했습니다: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.badRequest("보내지 못했습니다");
        }
    }

    /**
     * 카카오워크: 멤버 이메일로 사용자를 찾아 봇이 채널 이름으로 그룹 채팅방을 만들고 연결한다.
     * 이미 연결된 방이 있어도 새로 만든다 (멤버를 크게 바꿨을 때). 만든 뒤 방에 안내 메시지를 보낸다.
     */
    @Transactional
    public List<ChannelDto> openRoom(long id) {
        CurrentAdmin.requireSuper();
        ChannelRow c = find(id);
        if (!c.kind().equals("KAKAOWORK")) {
            throw ApiException.badRequest("카카오워크 채널만 채팅방을 만듭니다");
        }
        List<String> emails = emails(c.recipients());
        if (emails.isEmpty()) {
            throw ApiException.badRequest("채팅방에 넣을 멤버 이메일을 먼저 등록해 주세요");
        }
        try {
            String roomId = kakaoWork.openGroup(c.webhookUrl(), kakaoWork.findUserIds(c.webhookUrl(), emails), c.name());
            notifyMapper.updateChannelConversation(id, roomId);
            kakaoWork.send(c.webhookUrl(), roomId, KakaoWorkClient.text("INFO", "안내", "알림 채팅방을 만들었습니다",
                    "im010 백오피스 알림이 이 방으로 옵니다. 받을 알림 종류는 백오피스 관리자관리 › 알림 채널에서 바꿉니다."));
            audit.action(PROGRAM, "NOTIFY_KAKAOWORK_ROOM", c.name(), emails.size() + "명");
            return channels();
        } catch (IOException e) {
            audit.action(PROGRAM, "NOTIFY_KAKAOWORK_ROOM", c.name(), "FAIL " + e.getMessage());
            throw ApiException.badRequest("채팅방을 만들지 못했습니다: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.badRequest("채팅방을 만들지 못했습니다");
        }
    }

    /** 알림 경로 전체 확인: 알림함에 테스트 알림을 넣는다 → 배치가 그 종류를 받는 채널 · 메일로 보낸다 */
    @Transactional
    public void enqueueTest(String alertType) {
        CurrentAdmin.requireSuper();
        AlertType type = type(alertType);
        notifyMapper.insertNotification(type.name(), "INFO", "테스트 알림 — " + type.label(),
                CurrentAdmin.get().name() + " 님이 보낸 테스트 알림입니다. 이 종류를 받는 채널 · 메일로 발송됩니다.", null);
        audit.action(PROGRAM, "NOTIFY_TEST", type.name(), null);
    }

    public HistoryPage history(OffsetDateTime from, OffsetDateTime to, String alertType, String status, int page) {
        int p = Math.max(page, 1);
        String t = Texts.trim(alertType);
        String s = Texts.trim(status);
        return new HistoryPage(notifyMapper.countNotifications(from, to, t, s), p,
                notifyMapper.findNotifications(from, to, t, s, 50, (p - 1) * 50));
    }

    public MyNotify mine() {
        MailRecipientRow row = notifyMapper.findMailSetting(CurrentAdmin.get().id());
        return new MyNotify(row.email(), Texts.split(row.mailAlerts()), types());
    }

    @Transactional
    public MyNotify updateMine(String email, List<String> mailAlerts) {
        String e = Texts.trim(email);
        if (e != null && (e.length() > 100 || !e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) {
            throw ApiException.badRequest("메일 주소 형식이 올바르지 않습니다");
        }
        List<String> alerts = mailAlerts == null ? List.of() : mailAlerts.stream().distinct().map(a -> type(a).name()).toList();
        if (e == null && !alerts.isEmpty()) {
            throw ApiException.badRequest("메일로 받을 알림을 고르려면 메일 주소를 입력해 주세요");
        }
        notifyMapper.updateMailSetting(CurrentAdmin.get().id(), e, alerts.toArray(String[]::new));
        audit.action(PROGRAM, "MY_NOTIFY_UPDATE", CurrentAdmin.get().loginId(), String.join(",", alerts));
        return mine();
    }

    private ChannelRequest validate(ChannelRequest r, String currentUrl) {
        String name = Texts.trim(r.name());
        if (name == null || name.length() > 50) {
            throw ApiException.badRequest("채널 이름은 1 ~ 50자여야 합니다");
        }
        if (r.kind() == null || !KINDS.contains(r.kind())) {
            throw ApiException.badRequest("메신저 종류를 골라 주세요");
        }
        boolean kakao = r.kind().equals("KAKAOWORK");
        String url = Texts.trim(r.webhookUrl());
        if (url == null && currentUrl != null) {
            url = currentUrl;
        } else if (kakao) {
            if (url == null || url.length() > 200 || url.contains(" ")) {
                throw ApiException.badRequest("카카오워크 봇 App Key 를 입력해 주세요");
            }
        } else if (url == null || url.length() > 1000 || !url.startsWith("https://") || url.contains(" ")) {
            throw ApiException.badRequest("웹훅 주소는 https:// 로 시작해야 합니다");
        }
        String recipients = null;
        if (kakao) {
            if (name.length() > 60) {
                throw ApiException.badRequest("카카오워크 채팅방 이름(채널 이름)은 60자 이하여야 합니다");
            }
            List<String> emails = emails(r.recipients());
            if (emails.isEmpty()) {
                throw ApiException.badRequest("채팅방에 넣을 멤버 이메일을 한 명 이상 입력해 주세요");
            }
            if (emails.size() > MAX_RECIPIENTS) {
                throw ApiException.badRequest("멤버는 " + MAX_RECIPIENTS + "명까지 등록할 수 있습니다");
            }
            for (String e : emails) {
                if (e.length() > 100 || !EMAIL.matcher(e).matches()) {
                    throw ApiException.badRequest("이메일 형식이 올바르지 않습니다: " + e);
                }
            }
            recipients = String.join("\n", emails);
        }
        List<String> types = r.alertTypes() == null ? List.of() : r.alertTypes().stream().distinct().map(t -> type(t).name()).toList();
        return new ChannelRequest(name, r.kind(), url, types, r.enabled(), recipients);
    }

    private ChannelRow find(long id) {
        ChannelRow c = notifyMapper.findChannel(id);
        if (c == null) {
            throw ApiException.notFound("알림 채널");
        }
        return c;
    }

    private static AlertType type(String code) {
        try {
            return AlertType.valueOf(code);
        } catch (RuntimeException e) {
            throw ApiException.badRequest("알 수 없는 알림 종류: " + code);
        }
    }

    static ChannelDto dto(ChannelRow c) {
        String masked = c.kind().equals("KAKAOWORK") ? maskKey(c.webhookUrl()) : mask(c.webhookUrl());
        return new ChannelDto(c.id(), c.name(), c.kind(), masked, Texts.split(c.alertTypes()), c.enabled(),
                c.updatedBy(), c.updatedAt(), emails(c.recipients()), c.conversationId() != null);
    }

    /** 줄바꿈 · 쉼표 · 세미콜론 · 공백으로 나눈 이메일 (소문자, 중복 제거) */
    static List<String> emails(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[\\s,;]+")).map(String::trim).filter(e -> !e.isEmpty())
                .map(e -> e.toLowerCase(Locale.ROOT)).distinct().toList();
    }

    /** 봇 App Key → 앞 4자리… */
    static String maskKey(String key) {
        return key == null ? null : key.substring(0, Math.min(4, key.length())) + "…";
    }

    /** https://hooks.slack.com/services/T000/B000/XXXX → https://hooks.slack.com/serv… */
    static String mask(String url) {
        if (url == null) {
            return null;
        }
        int host = url.indexOf('/', "https://".length());
        int keep = host < 0 ? Math.min(url.length(), 20) : Math.min(url.length(), host + 5);
        return url.substring(0, keep) + "…";
    }
}

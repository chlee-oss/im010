package kr.co.im010.admin.settings;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.im010.admin.audit.AuditService;
import kr.co.im010.admin.auth.CurrentAdmin;
import kr.co.im010.admin.web.ApiException;
import kr.co.im010.admin.web.Texts;
import kr.co.im010.core.mapper.NotifyMapper;
import kr.co.im010.core.notify.AlertType;
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
    private static final Set<String> KINDS = Set.of("SLACK", "TEAMS", "JANDI", "WEBHOOK");

    public record AlertTypeDto(String code, String label, boolean messengerDefault) {
    }

    /** webhookUrl 은 가린 값 (예: https://hooks.slack.com/serv…) */
    public record ChannelDto(long id, String name, String kind, String webhookUrl, List<String> alertTypes, boolean enabled,
                             String updatedBy, OffsetDateTime updatedAt) {
    }

    public record ChannelRequest(String name, String kind, String webhookUrl, List<String> alertTypes, boolean enabled) {
    }

    public record MyNotify(String email, List<String> mailAlerts, List<AlertTypeDto> types) {
    }

    public record HistoryPage(int total, int page, List<NotificationRow> items) {
    }

    private final NotifyMapper notifyMapper;
    private final WebhookSender webhook;
    private final AuditService audit;

    public NotifyAdminService(NotifyMapper notifyMapper, WebhookSender webhook, AuditService audit) {
        this.notifyMapper = notifyMapper;
        this.webhook = webhook;
        this.audit = audit;
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
                CurrentAdmin.get().loginId());
        audit.action(PROGRAM, "NOTIFY_CHANNEL_CREATE", r.name(), r.kind());
        return channels();
    }

    /** 웹훅 주소를 비워 보내면 기존 주소를 그대로 둔다 (화면에는 가린 주소만 있으므로) */
    @Transactional
    public List<ChannelDto> updateChannel(long id, ChannelRequest req) {
        CurrentAdmin.requireSuper();
        ChannelRow current = find(id);
        ChannelRequest r = validate(req, current.webhookUrl());
        notifyMapper.updateChannel(id, r.name(), r.kind(), r.webhookUrl(), r.alertTypes().toArray(String[]::new), r.enabled(),
                CurrentAdmin.get().loginId());
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
        try {
            webhook.send(c.kind(), c.webhookUrl(), "INFO", "테스트", "알림 채널 연결 확인",
                    CurrentAdmin.get().name() + " 님이 백오피스에서 보낸 테스트 메시지입니다.");
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
        String url = Texts.trim(r.webhookUrl());
        if (url == null && currentUrl != null) {
            url = currentUrl;
        } else if (url == null || url.length() > 1000 || !url.startsWith("https://") || url.contains(" ")) {
            throw ApiException.badRequest("웹훅 주소는 https:// 로 시작해야 합니다");
        }
        List<String> types = r.alertTypes() == null ? List.of() : r.alertTypes().stream().distinct().map(t -> type(t).name()).toList();
        return new ChannelRequest(name, r.kind(), url, types, r.enabled());
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
        return new ChannelDto(c.id(), c.name(), c.kind(), mask(c.webhookUrl()), Texts.split(c.alertTypes()), c.enabled(),
                c.updatedBy(), c.updatedAt());
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

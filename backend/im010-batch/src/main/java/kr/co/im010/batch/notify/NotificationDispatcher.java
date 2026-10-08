package kr.co.im010.batch.notify;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import kr.co.im010.batch.CrawlProperties;
import kr.co.im010.core.mapper.NotifyMapper;
import kr.co.im010.core.notify.AlertType;
import kr.co.im010.core.notify.WebhookSender;
import kr.co.im010.core.row.ChannelRow;
import kr.co.im010.core.row.DeliveryRow;
import kr.co.im010.core.row.MailRecipientRow;
import kr.co.im010.core.row.NotificationRow;

/**
 * 알림 발송: 30초마다 알림함의 새 알림을 받을 대상(메신저 채널 · 메일 수신자)별 발송 건으로 펼치고, 보낼 차례인 건을 보낸다.
 * 실패하면 5분 · 10분 뒤 다시 보내고 3번 실패하면 포기한다. 메일 서버(SPRING_MAIL_HOST)가 없으면 메일 건은 바로 실패 처리.
 */
@Component
@ConditionalOnProperty(prefix = "im010.crawl", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    static final int MAX_ATTEMPTS = 3;

    private final NotifyMapper notifyMapper;
    private final WebhookSender webhook;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final CrawlProperties props;

    public NotificationDispatcher(NotifyMapper notifyMapper, WebhookSender webhook, ObjectProvider<JavaMailSender> mailSender,
                                  CrawlProperties props) {
        this.notifyMapper = notifyMapper;
        this.webhook = webhook;
        this.mailSender = mailSender;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${im010.notify.poll-interval:PT30S}", initialDelayString = "PT15S")
    public void dispatch() {
        expand();
        for (DeliveryRow d : notifyMapper.findDueDeliveries(100)) {
            send(d);
        }
        notifyMapper.settleNotifications();
    }

    /** 새 알림 → 대상별 발송 건. 받을 대상이 없으면 NO_TARGET. */
    void expand() {
        for (NotificationRow n : notifyMapper.findPending(100)) {
            List<ChannelRow> channels = notifyMapper.findChannelsFor(n.alertType());
            List<MailRecipientRow> mails = notifyMapper.findMailRecipientsFor(n.alertType());
            if (channels.isEmpty() && mails.isEmpty()) {
                notifyMapper.setNotificationStatus(n.id(), "NO_TARGET");
                continue;
            }
            for (ChannelRow c : channels) {
                notifyMapper.insertDelivery(n.id(), c.id(), c.name());
            }
            for (MailRecipientRow m : mails) {
                notifyMapper.insertDelivery(n.id(), null, m.email());
            }
        }
    }

    void send(DeliveryRow d) {
        int attempts = d.attempts() + 1;
        try {
            if (d.webhookUrl() != null) {
                webhook.send(d.channelKind(), d.webhookUrl(), d.level(), label(d.alertType()), d.title(), d.body());
            } else {
                JavaMailSender mail = mailSender.getIfAvailable();
                if (mail == null) {
                    notifyMapper.markDeliveryFailed(d.id(), attempts, "메일 서버 설정(SPRING_MAIL_HOST)이 없습니다", true, 0);
                    return;
                }
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(props.mailFrom());
                msg.setTo(d.target());
                msg.setSubject("[im010 · " + label(d.alertType()) + "] " + d.title());
                msg.setText((d.body() == null ? "" : d.body()) + "\n\n— im010 백오피스 알림 (수신 설정: 관리자관리 > 내 알림 설정)");
                mail.send(msg);
            }
            notifyMapper.markDelivered(d.id());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            boolean giveUp = attempts >= MAX_ATTEMPTS;
            log.warn("알림 발송 실패 ({}회) {} → {}: {}", attempts, d.title(), d.target(), e.getMessage());
            notifyMapper.markDeliveryFailed(d.id(), attempts, String.valueOf(e.getMessage()), giveUp, 5 * attempts);
        }
    }

    private static String label(String alertType) {
        try {
            return AlertType.valueOf(alertType).label();
        } catch (IllegalArgumentException e) {
            return alertType;
        }
    }
}

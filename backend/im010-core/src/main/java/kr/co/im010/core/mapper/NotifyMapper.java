package kr.co.im010.core.mapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import kr.co.im010.core.row.ChannelRow;
import kr.co.im010.core.row.CountRow;
import kr.co.im010.core.row.DeliveryRow;
import kr.co.im010.core.row.MailRecipientRow;
import kr.co.im010.core.row.NotificationRow;
import kr.co.im010.core.row.RunSummaryRow;
import kr.co.im010.core.row.StaleDataRow;

/** 알림함 · 발송 · 메신저 채널 · 메일 수신 설정 (백오피스 7장). */
@Mapper
public interface NotifyMapper {

    /** 알림함에 넣는다. dedupeKey 가 있으면 24시간 안에 같은 키가 있을 때 넣지 않는다. 넣은 건수 (0 또는 1). */
    int insertNotification(@Param("alertType") String alertType, @Param("level") String level,
                           @Param("title") String title, @Param("body") String body, @Param("dedupeKey") String dedupeKey);

    // ---------- 발송 (배치) ----------

    List<NotificationRow> findPending(@Param("limit") int limit);

    List<ChannelRow> findChannelsFor(@Param("alertType") String alertType);

    List<MailRecipientRow> findMailRecipientsFor(@Param("alertType") String alertType);

    void insertDelivery(@Param("notificationId") long notificationId, @Param("channelId") Long channelId,
                        @Param("target") String target);

    void setNotificationStatus(@Param("id") long id, @Param("status") String status);

    List<DeliveryRow> findDueDeliveries(@Param("limit") int limit);

    void markDelivered(@Param("id") long id);

    void markDeliveryFailed(@Param("id") long id, @Param("attempts") int attempts, @Param("error") String error,
                            @Param("giveUp") boolean giveUp, @Param("retryMinutes") int retryMinutes);

    /** 발송이 모두 끝난 알림의 상태를 정리 (SENT · PARTIAL · FAILED) */
    int settleNotifications();

    int deleteOlderThan(@Param("before") OffsetDateTime before);

    // ---------- 확인 작업 (배치) ----------

    /** 점검 대기 · 승인 대기가 기준 시간보다 오래된 건수 (status 별) */
    List<CountRow> countDelayedItems(@Param("before") OffsetDateTime before);

    // ---------- 정기 현황 리포트 (매일 08:00) ----------

    /**
     * 요금제 현황 (판매 종료 제외). key = 구분:유형 — PUBLISHED:POSTPAID 게시 중 · UNPUBLISHED 게시 전 · HIDDEN 비노출,
     * CHANGE_DRAFT 변경 대기 · CHANGE_SCHEDULED 변경 예약 (게시하지 않은 새 값이 있는 요금제)
     */
    List<CountRow> countPlanStatus();

    /** 이 날 판매 종료된 요금제 (유형별) */
    List<CountRow> countEndedOn(@Param("day") LocalDate day);

    /** 기간 안 고객 이동 (key = PLAN 개통하기 · INTERNET 상담 신청, label = 결과 FORWARDED · ENDED · NO_URL · HIDDEN) */
    List<CountRow> countForwards(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    /** 점검 대기 · 승인 대기 건수 (key = REVIEW_PENDING · APPROVAL_REQUESTED) */
    List<CountRow> countOpenItems();

    /** 게시 중 요금제가 있는 제휴사의 최근 수집일 */
    List<StaleDataRow> findLastCollectedByPartner();

    /** 하루 수집 결과 (제휴사 · 유형별 마지막 실행) */
    List<RunSummaryRow> findRunSummary(@Param("runOn") LocalDate runOn);

    // ---------- 채널 · 수신 설정 (백오피스) ----------

    List<ChannelRow> findChannels();

    ChannelRow findChannel(@Param("id") long id);

    long insertChannel(@Param("name") String name, @Param("kind") String kind, @Param("webhookUrl") String webhookUrl,
                       @Param("alertTypes") String[] alertTypes, @Param("enabled") boolean enabled,
                       @Param("recipients") String recipients, @Param("by") String by);

    void updateChannel(@Param("id") long id, @Param("name") String name, @Param("kind") String kind,
                       @Param("webhookUrl") String webhookUrl, @Param("alertTypes") String[] alertTypes,
                       @Param("enabled") boolean enabled, @Param("recipients") String recipients, @Param("by") String by);

    /** 카카오워크: 봇이 만든 채팅방 ID (null 이면 지움) */
    void updateChannelConversation(@Param("id") long id, @Param("conversationId") String conversationId);

    void deleteChannel(@Param("id") long id);

    MailRecipientRow findMailSetting(@Param("adminId") long adminId);

    void updateMailSetting(@Param("adminId") long adminId, @Param("email") String email,
                           @Param("mailAlerts") String[] mailAlerts);

    List<NotificationRow> findNotifications(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to,
                                            @Param("alertType") String alertType, @Param("status") String status,
                                            @Param("limit") int limit, @Param("offset") int offset);

    int countNotifications(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to,
                           @Param("alertType") String alertType, @Param("status") String status);
}

package kr.co.im010.core.row;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 요금제관리(PR-01) 목록 · 상세의 요금제 한 건. 이름 · 요금 · 망은 대기 버전이 있으면 대기 버전, 없으면 게시 중 버전 값.
 *
 * @param draftVersionId 게시 전 버전 (게시 대기 · 예약), 없으면 null
 */
public record PlanAdminRow(
        long id,
        String partnerCode,
        String partnerName,
        String planType,
        String partnerPlanCode,
        String status,
        String activationUrl,
        Long publishedVersionId,
        Long draftVersionId,
        String name,
        Integer price,
        String network,
        OffsetDateTime publishedAt,
        OffsetDateTime publishAt,
        LocalDate lastCollectedOn,
        LocalDate endedOn
) {
}

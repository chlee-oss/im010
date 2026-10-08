package kr.co.im010.core.row;

import java.time.OffsetDateTime;

/** 접속 · 처리 이력 (ST-05) 한 건. */
public record AuditListRow(
        long id,
        Long adminId,
        String loginId,
        String kind,
        String programId,
        String action,
        String target,
        String detail,
        String ip,
        OffsetDateTime createdAt
) {
}

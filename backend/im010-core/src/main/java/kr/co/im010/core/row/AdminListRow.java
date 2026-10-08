package kr.co.im010.core.row;

import java.time.OffsetDateTime;

/** 관리자관리(ST-02) 목록의 관리자 한 건 (비밀번호 · OTP 비밀키 제외). */
public record AdminListRow(
        long id,
        String loginId,
        String name,
        String dept,
        String phone,
        long groupId,
        String groupName,
        boolean groupSystem,
        String status,
        boolean otpEnabled,
        boolean mustChangePassword,
        OffsetDateTime lockedUntil,
        OffsetDateTime lastLoginAt,
        OffsetDateTime createdAt
) {
}

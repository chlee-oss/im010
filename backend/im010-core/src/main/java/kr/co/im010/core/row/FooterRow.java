package kr.co.im010.core.row;

import java.time.OffsetDateTime;

/** Footer 버전 (ST-06). 가장 최근 버전이 프런트에 노출된다. */
public record FooterRow(
        long id,
        String companyName,
        String ceo,
        String businessNo,
        String mailOrderNo,
        String address,
        String csPhone,
        String csHours,
        String email,
        String notice,
        String createdBy,
        OffsetDateTime createdAt
) {
}

package kr.co.im010.core.row;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 약관 버전 (ST-04). status = DRAFT | PUBLISHED */
public record TermsRow(
        long id,
        String termsType,
        String version,
        String body,
        LocalDate effectiveOn,
        String status,
        String createdBy,
        OffsetDateTime createdAt,
        String publishedBy,
        OffsetDateTime publishedAt
) {
}

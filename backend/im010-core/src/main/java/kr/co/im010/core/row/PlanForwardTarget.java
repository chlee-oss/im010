package kr.co.im010.core.row;

/** 개통하기 포워딩 판단에 필요한 요금제 정보. */
public record PlanForwardTarget(
        long id,
        String partnerCode,
        String planType,
        String status,
        String activationUrl,
        Long publishedVersionId
) {
}

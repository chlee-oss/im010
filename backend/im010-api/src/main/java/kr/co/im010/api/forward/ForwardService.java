package kr.co.im010.api.forward;

import java.net.URI;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import kr.co.im010.api.config.Im010Properties;
import kr.co.im010.api.web.NotFoundException;
import kr.co.im010.core.mapper.InternetProductMapper;
import kr.co.im010.core.mapper.PlanMapper;
import kr.co.im010.core.row.ForwardLog;
import kr.co.im010.core.row.InternetForwardTarget;
import kr.co.im010.core.row.PlanForwardTarget;

/**
 * 개통하기(RC-01) · 인터넷 신청(RC-02) 포워딩.
 * 이동할 URL은 DB에 등록된 값만 사용한다 (요청 파라미터로 받은 URL로는 보내지 않음).
 */
@Service
public class ForwardService {

    private static final Pattern FROM_PAGE = Pattern.compile("[A-Za-z0-9_-]{1,30}");

    private final PlanMapper planMapper;
    private final InternetProductMapper internetProductMapper;
    private final ForwardLogWriter logWriter;
    private final Im010Properties props;

    public ForwardService(PlanMapper planMapper, InternetProductMapper internetProductMapper,
                          ForwardLogWriter logWriter, Im010Properties props) {
        this.planMapper = planMapper;
        this.internetProductMapper = internetProductMapper;
        this.logWriter = logWriter;
        this.props = props;
    }

    public URI forwardPlan(long planId, String from) {
        PlanForwardTarget t = planMapper.findForwardTarget(planId);
        if (t == null) {
            throw new NotFoundException("plan " + planId);
        }
        String result;
        String target;
        if ("PUBLISHED".equals(t.status()) && hasText(t.activationUrl())) {
            result = "FORWARDED";
            target = t.activationUrl();
        } else if ("PUBLISHED".equals(t.status())) {
            result = "NO_URL";
            target = props.frontBaseUrl() + "/plans/" + planId + "?forward=unavailable";
        } else if ("ENDED".equals(t.status())) {
            // 판매 종료 요금제는 이동하지 않고 상세로 돌려보내 안내한다
            result = "ENDED";
            target = props.frontBaseUrl() + "/plans/" + planId + "?ended=1";
        } else {
            result = "HIDDEN";
            target = props.frontBaseUrl() + "/";
        }
        logWriter.write(new ForwardLog("PLAN", planId, t.partnerCode(), t.planType(),
                "FORWARDED".equals(result) ? target : null, sanitizeFrom(from), result, t.publishedVersionId()));
        return URI.create(target);
    }

    public URI forwardInternet(long productId, String from) {
        InternetForwardTarget t = internetProductMapper.findForwardTarget(productId);
        if (t == null) {
            throw new NotFoundException("internet product " + productId);
        }
        String result;
        String target;
        if (!t.exposed()) {
            result = "HIDDEN";
            target = props.frontBaseUrl() + "/#internet-compare";
        } else if (!hasText(t.applyUrl())) {
            result = "NO_URL";
            target = props.frontBaseUrl() + "/?forward=unavailable#internet-compare";
        } else {
            result = "FORWARDED";
            target = t.applyUrl();
        }
        String partnerRef = t.internetPartnerId() == null ? null : String.valueOf(t.internetPartnerId());
        logWriter.write(new ForwardLog("INTERNET", productId, partnerRef, null,
                "FORWARDED".equals(result) ? target : null, sanitizeFrom(from), result, null));
        return URI.create(target);
    }

    static String sanitizeFrom(String from) {
        return from != null && FROM_PAGE.matcher(from).matches() ? from : "UNKNOWN";
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}

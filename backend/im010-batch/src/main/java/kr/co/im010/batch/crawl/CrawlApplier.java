package kr.co.im010.batch.crawl;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.im010.batch.CrawlProperties;
import kr.co.im010.batch.crawl.ChangeDetector.Change;
import kr.co.im010.batch.crawl.ChangeDetector.Item;
import kr.co.im010.batch.crawl.ChangeDetector.Status;
import kr.co.im010.core.mapper.CrawlItemMapper;
import kr.co.im010.core.mapper.CrawlMapper;
import kr.co.im010.core.parse.ParsedPlan;
import kr.co.im010.core.row.CrawlItemRow;
import kr.co.im010.core.row.CrawlJobRow;
import kr.co.im010.core.row.CrawlRunRow;
import kr.co.im010.core.row.MonthlyPickRow;
import kr.co.im010.core.row.OpenItemRow;
import kr.co.im010.core.row.PlanBaselineRow;
import kr.co.im010.core.row.PlanCodeRow;

/**
 * 유형별 수집 결과를 DB 에 반영한다. 실행 기록 · 수집 건 · 판매 종료 · 이달의 요금제 변경을 한 트랜잭션으로 묶는다.
 */
@Service
public class CrawlApplier {

    /** 반영 결과 요약 (알림 · 로그용). */
    public record Summary(String urlType, String result, int collected, int newCount, int changedCount,
                          List<String> endedNames, int hiddenPicks, String message) {
    }

    private final CrawlMapper crawlMapper;
    private final CrawlItemMapper itemMapper;
    private final CrawlProperties props;

    public CrawlApplier(CrawlMapper crawlMapper, CrawlItemMapper itemMapper, CrawlProperties props) {
        this.crawlMapper = crawlMapper;
        this.itemMapper = itemMapper;
        this.props = props;
    }

    /** 실패 · 수집 이상: 실행 기록만 남기고 아무것도 반영하지 않는다 (6.3). */
    public Summary recordFailure(CrawlJobRow job, String urlType, String result, OffsetDateTime startedAt,
                                 int urlCount, int collected, String message) {
        crawlMapper.insertRun(new CrawlRunRow(job.id(), job.partnerCode(), urlType, job.runOn(), result,
                urlCount, collected, 0, 0, 0, message, startedAt));
        return new Summary(urlType, result, collected, 0, 0, List.of(), 0, message);
    }

    /** 후불 · 선불: 변경 감지 → 수집 건 저장 → 판매 종료 자동 처리. */
    @Transactional
    public Summary applyPlans(CrawlJobRow job, String urlType, OffsetDateTime startedAt, int urlCount, List<ParsedPlan> plans) {
        String partner = job.partnerCode();
        List<PlanBaselineRow> baselines = itemMapper.findBaselines(partner, urlType);
        ChangeDetector.Result r = ChangeDetector.detect(plans, baselines,
                itemMapper.findOpenItems(partner, urlType), itemMapper.findHandledHashes(partner, urlType),
                props.priceJumpRatio());

        int newCount = pending(r.items(), Change.NEW);
        int changedCount = pending(r.items(), Change.CHANGED);
        long runId = crawlMapper.insertRun(new CrawlRunRow(job.id(), partner, urlType, job.runOn(), "SUCCESS",
                urlCount, plans.size(), newCount, changedCount, r.endedPlanIds().size(), null, startedAt));

        List<String> endedNames = new ArrayList<>();
        for (Item item : r.items()) {
            itemMapper.insertItem(toRow(runId, job, urlType, item, null));
            if (item.change() == Change.ENDED) {
                endedNames.add(item.ended().name());
            }
        }
        if (!r.supersededItemIds().isEmpty()) {
            itemMapper.supersedeItems(r.supersededItemIds());
        }
        if (!r.touchedPlanIds().isEmpty()) {
            itemMapper.touchCollected(r.touchedPlanIds(), job.runOn());
        }
        int hidden = 0;
        if (!r.endedPlanIds().isEmpty()) {
            itemMapper.endPlans(r.endedPlanIds(), job.runOn());
            hidden = itemMapper.hidePicksOfPlans(r.endedPlanIds());
        }
        return new Summary(urlType, "SUCCESS", plans.size(), newCount, changedCount, endedNames, hidden, null);
    }

    /**
     * 이달의 요금제 (8장): 노출 중인 항목은 사이트 순서만 갱신, 새 항목은 게시 중 요금제와 연결해 점검 대기,
     * 사이트에서 빠진 항목은 자동 제외.
     */
    @Transactional
    public Summary applyMonthly(CrawlJobRow job, OffsetDateTime startedAt, int urlCount, List<ParsedPlan> plans) {
        String partner = job.partnerCode();
        String type = "MONTHLY";
        Map<String, MonthlyPickRow> pickByCode = new HashMap<>();
        List<MonthlyPickRow> picks = itemMapper.findExposedPicks(partner);
        for (MonthlyPickRow pick : picks) {
            if (pick.partnerPlanCode() != null) {
                pickByCode.put(pick.partnerPlanCode(), pick);
            }
        }
        List<String> codes = plans.stream().map(ParsedPlan::partnerPlanCode).filter(c -> c != null).toList();
        Map<String, PlanCodeRow> planByCode = new HashMap<>();
        if (!codes.isEmpty()) {
            for (PlanCodeRow row : itemMapper.findPlansByCodes(partner, codes)) {
                planByCode.put(row.partnerPlanCode(), row);
            }
        }
        Map<String, OpenItemRow> openByKey = new HashMap<>();
        for (OpenItemRow o : itemMapper.findOpenItems(partner, type)) {
            openByKey.put(o.itemKey(), o);
        }

        List<Item> items = new ArrayList<>();
        List<Integer> orders = new ArrayList<>();
        List<Long> superseded = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();
        Set<String> seenKeys = new HashSet<>();
        for (int i = 0; i < plans.size(); i++) {
            ParsedPlan p = plans.get(i);
            int order = i + 1;
            String key = p.key();
            seenKeys.add(key);
            if (p.partnerPlanCode() != null) {
                seenCodes.add(p.partnerPlanCode());
            }
            String hash = ChangeDetector.hash(p);
            OpenItemRow open = openByKey.get(key);
            MonthlyPickRow pick = p.partnerPlanCode() != null ? pickByCode.get(p.partnerPlanCode()) : null;
            if (pick != null) {
                if (pick.siteOrder() != order) {
                    itemMapper.updatePickOrder(pick.id(), order, job.runOn());
                }
                if (open != null) {
                    superseded.add(open.id());
                }
                items.add(new Item(p, null, key, pick.planId(), Change.UNCHANGED, Status.RECORDED, List.of(), List.of(), hash));
            } else {
                PlanCodeRow plan = p.partnerPlanCode() != null ? planByCode.get(p.partnerPlanCode()) : null;
                List<String> warnings = ChangeDetector.warnings(p);
                if (plan == null) {
                    warnings.add("UNMATCHED");   // 연결할 요금제가 아직 게시되지 않음
                }
                Status status = open != null && hash.equals(open.valueHash()) ? Status.RECORDED : Status.REVIEW_PENDING;
                if (open != null && status == Status.REVIEW_PENDING) {
                    superseded.add(open.id());
                }
                items.add(new Item(p, null, key, plan != null ? plan.planId() : null, Change.NEW, status, List.of(), warnings, hash));
            }
            orders.add(order);
        }

        List<Long> hiddenIds = new ArrayList<>();
        List<MonthlyPickRow> hiddenPicks = new ArrayList<>();
        for (MonthlyPickRow pick : picks) {
            if (pick.partnerPlanCode() == null || !seenCodes.contains(pick.partnerPlanCode())) {
                hiddenIds.add(pick.id());
                hiddenPicks.add(pick);
            }
        }
        for (OpenItemRow o : openByKey.values()) {
            if (!seenKeys.contains(o.itemKey()) && !superseded.contains(o.id())) {
                superseded.add(o.id());
            }
        }

        int newCount = pending(items, Change.NEW);
        long runId = crawlMapper.insertRun(new CrawlRunRow(job.id(), partner, type, job.runOn(), "SUCCESS",
                urlCount, plans.size(), newCount, 0, hiddenIds.size(), null, startedAt));
        for (int i = 0; i < items.size(); i++) {
            itemMapper.insertItem(toRow(runId, job, type, items.get(i), orders.get(i)));
        }
        for (MonthlyPickRow pick : hiddenPicks) {
            String key = pick.partnerPlanCode() != null ? pick.partnerPlanCode() : "pick:" + pick.id();
            itemMapper.insertItem(new CrawlItemRow(runId, partner, type, key, pick.partnerPlanCode(), pick.planId(),
                    Change.ENDED.name(), Status.AUTO_APPLIED.name(), null, null, null, null, null, null, null, null,
                    null, null, null, null, null, pick.siteOrder(), new String[0], new String[0], null, job.runOn()));
        }
        if (!hiddenIds.isEmpty()) {
            itemMapper.hidePicks(hiddenIds);
        }
        if (!superseded.isEmpty()) {
            itemMapper.supersedeItems(superseded);
        }
        return new Summary(type, "SUCCESS", plans.size(), newCount, 0, List.of(), hiddenIds.size(), null);
    }

    private static int pending(List<Item> items, Change change) {
        return (int) items.stream().filter(i -> i.change() == change && i.status() == Status.REVIEW_PENDING).count();
    }

    private static CrawlItemRow toRow(long runId, CrawlJobRow job, String urlType, Item item, Integer siteOrder) {
        String[] changed = item.changedFields().toArray(String[]::new);
        String[] warnings = item.warnings().toArray(String[]::new);
        ParsedPlan p = item.plan();
        if (p == null) {
            PlanBaselineRow b = item.ended();
            return new CrawlItemRow(runId, job.partnerCode(), urlType, item.key(), b.partnerPlanCode(), item.planId(),
                    item.change().name(), item.status().name(), b.name(), b.dataText(), b.dataGb(), b.qosText(),
                    b.voiceText(), b.smsText(), b.network(), b.generation(), b.price(), b.discountMonths(),
                    b.priceAfterDiscount(), null, null, null, changed, warnings, null, job.runOn());
        }
        return new CrawlItemRow(runId, job.partnerCode(), urlType, item.key(), p.partnerPlanCode(), item.planId(),
                item.change().name(), item.status().name(), p.name(), p.dataText(), p.dataGb(), p.qosText(),
                p.voiceText(), p.smsText(), p.network(), p.generation(), p.price(), p.discountMonths(),
                p.priceAfterDiscount(), p.detailUrl(), p.sourceUrl(), siteOrder != null ? siteOrder : p.siteOrder(),
                changed, warnings, item.valueHash(), job.runOn());
    }
}

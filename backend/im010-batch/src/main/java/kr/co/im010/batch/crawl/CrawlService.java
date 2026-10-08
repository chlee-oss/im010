package kr.co.im010.batch.crawl;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import kr.co.im010.batch.CrawlProperties;
import kr.co.im010.batch.notify.Notifier;
import kr.co.im010.batch.notify.Notifier.Level;
import kr.co.im010.core.notify.AlertType;
import kr.co.im010.core.mapper.CrawlMapper;
import kr.co.im010.core.parse.PageFetcher;
import kr.co.im010.core.parse.ParsedPage;
import kr.co.im010.core.parse.ParsedPlan;
import kr.co.im010.core.parse.RatePlanParser;
import kr.co.im010.core.parse.Urls;
import kr.co.im010.core.row.CollectUrlRow;
import kr.co.im010.core.row.CrawlJobRow;
import kr.co.im010.core.row.PartnerTabRow;

/**
 * 수집 작업 실행 (백오피스 6장).
 * 작업 1건 = 제휴사 1곳. 등록된 유형(후불 → 선불 → 이달의 요금제)마다 URL 을 모두 받아 합친 뒤,
 * 보호 조건(6.3)을 통과한 결과만 반영한다. 실패한 유형은 30분 뒤 재시도 작업으로 다시 넣는다.
 */
@Service
public class CrawlService {

    private static final Logger log = LoggerFactory.getLogger(CrawlService.class);
    private static final List<String> TYPE_ORDER = List.of("POSTPAID", "PREPAID", "MONTHLY");
    private static final Map<String, String> TYPE_LABEL = Map.of("POSTPAID", "후불", "PREPAID", "선불", "MONTHLY", "이달의 요금제");

    private final CrawlMapper crawlMapper;
    private final CrawlApplier applier;
    private final PageFetcher fetcher;
    private final RatePlanParser parser;
    private final Notifier notifier;
    private final CrawlProperties props;

    public CrawlService(CrawlMapper crawlMapper, CrawlApplier applier, PageFetcher fetcher, RatePlanParser parser,
                        Notifier notifier, CrawlProperties props) {
        this.crawlMapper = crawlMapper;
        this.applier = applier;
        this.fetcher = fetcher;
        this.parser = parser;
        this.notifier = notifier;
        this.props = props;
    }

    /** 대기 중인 작업을 모두 실행한다. 실행한 작업 수. */
    public int processQueue() {
        int done = 0;
        CrawlJobRow job;
        while ((job = crawlMapper.claimNextJob()) != null) {
            try {
                runJob(job);
            } catch (RuntimeException e) {
                log.error("crawl job {} ({}) failed", job.id(), job.partnerCode(), e);
                notifier.send(AlertType.CRAWL_FAILURE, Level.URGENT, "수집 작업 오류 — " + partnerName(job), e.toString());
            } finally {
                crawlMapper.finishJob(job.id());
            }
            done++;
        }
        return done;
    }

    void runJob(CrawlJobRow job) {
        List<CollectUrlRow> urls = crawlMapper.findCollectUrls(job.partnerCode());
        Set<String> wanted = job.urlTypes() == null || job.urlTypes().isBlank()
                ? Set.copyOf(TYPE_ORDER) : Set.of(job.urlTypes().split("\\|"));
        Map<String, List<CollectUrlRow>> byType = new LinkedHashMap<>();
        for (String type : TYPE_ORDER) {
            List<CollectUrlRow> list = urls.stream().filter(u -> u.urlType().equals(type)).toList();
            if (wanted.contains(type) && !list.isEmpty()) {
                byType.put(type, list);
            }
        }

        List<ParsedPage.Tab> discovered = new ArrayList<>();
        List<String> failedTypes = new ArrayList<>();
        List<CrawlApplier.Summary> summaries = new ArrayList<>();
        for (Map.Entry<String, List<CollectUrlRow>> e : byType.entrySet()) {
            CrawlApplier.Summary s = runType(job, e.getKey(), e.getValue(), discovered);
            summaries.add(s);
            if (s.result().equals("FAILED")) {
                failedTypes.add(e.getKey());
            }
            alert(job, s);
        }

        watchTabs(job, urls, discovered);

        if (!failedTypes.isEmpty() && job.attempt() < props.maxAttempts()) {
            crawlMapper.insertJob(job.partnerCode(), "RETRY", failedTypes.toArray(String[]::new), job.attempt() + 1,
                    job.runOn(), (int) props.retryDelay().toMinutes(), null);
        }
        // 제휴사별 완료는 로그만 — 운영자에게는 하루 한 번 수집 결과 요약을 보낸다 (AlertJobs)
        log.info("수집 완료 — {} : {}", job.partnerCode(), summaries.stream()
                .map(s -> "%s %s %d건 (신규 %d · 변경 %d · 종료 %d)".formatted(TYPE_LABEL.get(s.urlType()), s.result(),
                        s.collected(), s.newCount(), s.changedCount(), s.endedNames().size() + s.hiddenPicks()))
                .collect(Collectors.joining(" / ")));
    }

    private CrawlApplier.Summary runType(CrawlJobRow job, String type, List<CollectUrlRow> urls, List<ParsedPage.Tab> discovered) {
        OffsetDateTime startedAt = OffsetDateTime.now();
        List<ParsedPage> pages = new ArrayList<>();
        for (CollectUrlRow url : urls) {
            try {
                Document doc = fetcher.fetch(url.url());
                ParsedPage page = parser.parse(doc, url.url(), url.label());
                pages.add(page);
                discovered.addAll(page.tabs());
            } catch (IOException | IllegalArgumentException e) {
                return applier.recordFailure(job, type, "FAILED", startedAt, urls.size(), 0,
                        "접속 실패: " + url.url() + " (" + e.getMessage() + ")");
            }
        }

        // 보호 조건 (6.3): 하나라도 걸리면 판매 종료를 포함해 아무것도 반영하지 않는다
        List<ParsedPlan> all = pages.stream().flatMap(p -> p.plans().stream()).toList();
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).plans().isEmpty()) {
                return applier.recordFailure(job, type, "ABNORMAL", startedAt, urls.size(), all.size(),
                        "추출 0건: " + urls.get(i).url());
            }
        }
        long missing = all.stream().filter(ParsedPlan::missingRequired).count();
        if (missing > all.size() * props.maxFieldFailureRatio()) {
            return applier.recordFailure(job, type, "ABNORMAL", startedAt, urls.size(), all.size(),
                    "필수 항목 추출 실패 %d / %d건".formatted(missing, all.size()));
        }
        Map<String, ParsedPlan> unique = new LinkedHashMap<>();
        for (ParsedPlan p : all) {
            unique.putIfAbsent(p.key(), p);   // 여러 탭에 있으면 앞 순서 탭 값
        }
        List<ParsedPlan> plans = List.copyOf(unique.values());
        Integer last = crawlMapper.findLastSuccessCount(job.partnerCode(), type);
        if (last != null && plans.size() < last * props.minKeepRatio()) {
            return applier.recordFailure(job, type, "ABNORMAL", startedAt, urls.size(), plans.size(),
                    "직전 정상 수집 %d건 → %d건".formatted(last, plans.size()));
        }
        return type.equals("MONTHLY")
                ? applier.applyMonthly(job, startedAt, urls.size(), plans)
                : applier.applyPlans(job, type, startedAt, urls.size(), plans);
    }

    private void alert(CrawlJobRow job, CrawlApplier.Summary s) {
        String label = partnerName(job) + " " + TYPE_LABEL.get(s.urlType());
        if (!s.result().equals("SUCCESS")) {
            notifier.send(AlertType.CRAWL_FAILURE, Level.URGENT, "수집 " + (s.result().equals("FAILED") ? "실패" : "이상") + " — " + label,
                    s.message() + (s.result().equals("FAILED") && job.attempt() < props.maxAttempts() ? " · 재시도 예정" : ""));
            return;
        }
        if (!s.endedNames().isEmpty()) {
            notifier.send(AlertType.PLAN_ENDED, Level.WARN, "판매 종료 처리 — " + label, String.join(", ", s.endedNames()));
        }
        if (s.hiddenPicks() > 0) {
            notifier.send(AlertType.MONTHLY_REMOVED, Level.URGENT, "이달의 요금제 자동 제외 — " + partnerName(job), s.hiddenPicks() + "건");
        }
    }

    private String partnerName(CrawlJobRow job) {
        String name = crawlMapper.findPartnerName(job.partnerCode());
        return name != null ? name : job.partnerCode();
    }

    /** 사이트 메뉴의 탭과 등록된 수집 URL 을 비교해 새 탭 · 사라진 탭을 알린다. */
    private void watchTabs(CrawlJobRow job, List<CollectUrlRow> urls, List<ParsedPage.Tab> discovered) {
        if (discovered.isEmpty()) {
            return;
        }
        Map<String, String> tabs = new LinkedHashMap<>();
        for (ParsedPage.Tab t : discovered) {
            tabs.putIfAbsent(t.url(), t.label());
        }
        Set<String> registered = urls.stream().map(u -> Urls.clean(u.url())).collect(Collectors.toSet());
        Map<String, PartnerTabRow> known = new HashMap<>();
        for (PartnerTabRow row : crawlMapper.findTabs(job.partnerCode())) {
            known.put(row.url(), row);
        }
        List<String> newTabs = new ArrayList<>();
        for (Map.Entry<String, String> t : tabs.entrySet()) {
            if (registered.contains(t.getKey())) {
                continue;
            }
            PartnerTabRow row = known.get(t.getKey());
            if (row != null) {
                crawlMapper.touchTab(row.id(), t.getValue(), job.runOn());
            } else {
                crawlMapper.insertTab(job.partnerCode(), t.getKey(), t.getValue(), job.runOn());
                newTabs.add(t.getValue() + " " + t.getKey());
            }
        }
        if (!newTabs.isEmpty()) {
            notifier.send(AlertType.TAB_CHANGED, Level.WARN, "미등록 탭 발견 — " + partnerName(job),
                    String.join(", ", newTabs) + " → 제휴사관리에서 수집 URL 등록 또는 수집 안 함 처리");
        }
        List<String> gone = registered.stream()
                .filter(u -> Urls.param(u, "type") != null && !tabs.containsKey(u))
                .sorted().toList();
        if (!gone.isEmpty()) {
            notifier.send(AlertType.TAB_CHANGED, Level.WARN, "등록된 탭이 사이트 메뉴에 없음 — " + partnerName(job),
                    String.join(", ", gone) + " → 탭이 없어졌으면 수집 URL 정리");
        }
    }
}

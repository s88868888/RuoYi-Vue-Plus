package org.dromara.review.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.review.domain.ReviewStandardRule;
import org.dromara.review.domain.ReviewTask;
import org.dromara.review.mapper.ReviewStandardRuleMapper;
import org.dromara.review.mapper.ReviewTaskMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 审核统计仪表盘
 *
 * @author ruoyi
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/review/statistics")
public class ReviewStatisticsController extends BaseController {

    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewStandardRuleMapper reviewStandardRuleMapper;

    /**
     * 概览指标
     */
    @SaCheckPermission("review:task:list")
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        List<ReviewTask> tasks = reviewTaskMapper.selectList(Wrappers.lambdaQuery());
        int total = tasks.size();
        List<ReviewTask> auditTasks = tasks.stream().filter(t -> !isCompareTask(t)).toList();
        int auditTotal = auditTasks.size();
        long passCount = auditTasks.stream().filter(t -> "pass".equals(t.getPassStatus())).count();
        long failCount = auditTasks.stream().filter(t -> "fail".equals(t.getPassStatus())).count();
        OptionalDouble avgOpt = tasks.stream()
            .filter(t -> t.getReviewDuration() != null && t.getReviewDuration() > 0)
            .mapToLong(ReviewTask::getReviewDuration)
            .average();
        long avgDuration = avgOpt.isPresent() ? (long) avgOpt.getAsDouble() : 0;

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("passRate", auditTotal > 0 ? Math.round(passCount * 100.0 / auditTotal) + "%" : "0%");
        result.put("avgTime", avgDuration > 0 ? (avgDuration / 1000) + "s" : "0s");
        result.put("issueRate", auditTotal > 0 ? Math.round(failCount * 100.0 / auditTotal) + "%" : "0%");
        return R.ok(result);
    }

    /**
     * 审核趋势（近N天）
     */
    @SaCheckPermission("review:task:list")
    @GetMapping("/trend")
    public R<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "30") Integer days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);
        Date startTime = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

        List<ReviewTask> tasks = reviewTaskMapper.selectList(
            Wrappers.<ReviewTask>lambdaQuery()
                .ge(ReviewTask::getCreateTime, startTime)
        );

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Map<String, List<ReviewTask>> grouped = tasks.stream()
            .filter(t -> t.getCreateTime() != null)
            .collect(Collectors.groupingBy(t -> sdf.format(t.getCreateTime())));

        List<Map<String, Object>> result = new ArrayList<>();
        for (long i = 0; i < days; i++) {
            LocalDate date = startDate.plus(i, ChronoUnit.DAYS);
            String dateStr = date.toString();
            List<ReviewTask> dayTasks = grouped.getOrDefault(dateStr, Collections.emptyList());
            List<ReviewTask> dayAuditTasks = dayTasks.stream().filter(t -> !isCompareTask(t)).toList();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", dateStr);
            item.put("total", dayTasks.size());
            item.put("passCount", dayAuditTasks.stream().filter(t -> "pass".equals(t.getPassStatus())).count());
            item.put("failCount", dayAuditTasks.stream().filter(t -> "fail".equals(t.getPassStatus())).count());
            result.add(item);
        }
        return R.ok(result);
    }

    /**
     * 通过状态分布
     */
    @SaCheckPermission("review:task:list")
    @GetMapping("/passStatus")
    public R<List<Map<String, Object>>> passStatus() {
        List<ReviewTask> tasks = reviewTaskMapper.selectList(Wrappers.lambdaQuery()).stream()
            .filter(t -> !isCompareTask(t))
            .toList();
        long passCount = tasks.stream().filter(t -> "pass".equals(t.getPassStatus())).count();
        long failCount = tasks.stream().filter(t -> "fail".equals(t.getPassStatus())).count();
        long otherCount = tasks.size() - passCount - failCount;

        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> passItem = new LinkedHashMap<>();
        passItem.put("name", "通过");
        passItem.put("value", passCount);
        result.add(passItem);

        Map<String, Object> failItem = new LinkedHashMap<>();
        failItem.put("name", "不通过");
        failItem.put("value", failCount);
        result.add(failItem);

        Map<String, Object> otherItem = new LinkedHashMap<>();
        otherItem.put("name", "进行中");
        otherItem.put("value", otherCount);
        result.add(otherItem);

        return R.ok(result);
    }

    private boolean isCompareTask(ReviewTask task) {
        return task != null && task.getTaskType() != null
            && task.getTaskType().toUpperCase().contains("COMPARE");
    }

    /**
     * 规则命中排行Top10
     */
    @SaCheckPermission("review:task:list")
    @GetMapping("/ruleHitTop")
    public R<List<Map<String, Object>>> ruleHitTop() {
        List<ReviewStandardRule> rules = reviewStandardRuleMapper.selectList(Wrappers.lambdaQuery());
        List<Map<String, Object>> result = rules.stream()
            .filter(r -> r.getHitCount() != null && r.getHitCount() > 0)
            .sorted(Comparator.comparingInt(ReviewStandardRule::getHitCount).reversed())
            .limit(10)
            .map(r -> {
                Map<String, Object> item = new LinkedHashMap<>();
                String content = r.getContent() != null ? r.getContent() : "";
                item.put("ruleName", content.length() > 20 ? content.substring(0, 20) : content);
                item.put("hitCount", r.getHitCount());
                return item;
            })
            .collect(Collectors.toList());
        return R.ok(result);
    }

    /**
     * 近4周严重程度分布
     */
    @SaCheckPermission("review:task:list")
    @GetMapping("/severityWeekly")
    public R<List<Map<String, Object>>> severityWeekly() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusWeeks(4).plusDays(1);
        Date startTime = Date.from(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

        List<ReviewTask> tasks = reviewTaskMapper.selectList(
            Wrappers.<ReviewTask>lambdaQuery()
                .ge(ReviewTask::getCreateTime, startTime)
        );

        List<Map<String, Object>> result = new ArrayList<>();
        for (int w = 1; w <= 4; w++) {
            LocalDate weekStart = today.minusWeeks(4 - w).plusDays(1);
            LocalDate weekEnd = today.minusWeeks(3 - w);
            Date wStart = Date.from(weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date wEnd = Date.from(weekEnd.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

            long errorSum = 0, warningSum = 0, infoSum = 0;
            for (ReviewTask t : tasks) {
                if (t.getCreateTime() != null && !t.getCreateTime().before(wStart) && t.getCreateTime().before(wEnd)) {
                    errorSum += (t.getErrorCount() != null ? t.getErrorCount() : 0);
                    warningSum += (t.getWarningCount() != null ? t.getWarningCount() : 0);
                    infoSum += (t.getInfoCount() != null ? t.getInfoCount() : 0);
                }
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("week", "第" + w + "周");
            item.put("errorCount", errorSum);
            item.put("warningCount", warningSum);
            item.put("infoCount", infoSum);
            result.add(item);
        }
        return R.ok(result);
    }
}

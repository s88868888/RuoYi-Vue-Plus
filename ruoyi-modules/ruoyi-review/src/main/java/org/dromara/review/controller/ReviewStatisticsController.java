package org.dromara.review.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.dromara.review.domain.ReviewTask;
import org.dromara.review.mapper.ReviewTaskMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/review/statistics")
public class ReviewStatisticsController extends BaseController {

    private final ReviewTaskMapper reviewTaskMapper;

    @SaCheckPermission("review:task:list")
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        List<ReviewTask> tasks = reviewTaskMapper.selectList(Wrappers.lambdaQuery());
        int total = tasks.size();
        long passCount = tasks.stream().filter(t -> "pass".equals(t.getPassStatus())).count();
        long avgDuration = tasks.stream()
            .filter(t -> t.getReviewDuration() != null && t.getReviewDuration() > 0)
            .mapToLong(ReviewTask::getReviewDuration)
            .average().isPresent()
            ? (long) tasks.stream().filter(t -> t.getReviewDuration() != null && t.getReviewDuration() > 0)
                .mapToLong(ReviewTask::getReviewDuration).average().getAsDouble()
            : 0;
        long failCount = tasks.stream().filter(t -> "fail".equals(t.getPassStatus())).count();

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("passRate", total > 0 ? Math.round(passCount * 100.0 / total) + "%" : "0%");
        result.put("avgTime", avgDuration > 0 ? (avgDuration / 1000) + "s" : "0s");
        result.put("issueRate", total > 0 ? Math.round(failCount * 100.0 / total) + "%" : "0%");
        return R.ok(result);
    }
}

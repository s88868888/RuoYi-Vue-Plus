package org.dromara.review.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.review.agent.ReviewAgent;
import org.dromara.review.domain.ReviewTask;
import org.dromara.review.domain.vo.ReviewEngineCompareVo;
import org.dromara.review.graph.ReviewGraphAgent;
import org.dromara.review.mapper.ReviewTaskMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 双引擎(legacy vs graph)审核对比评测服务。
 * <p>
 * 对同一批任务分别用两种引擎 dry-run（只算不落库，不污染生产数据），提取指标对比，
 * 量化 graph agentic 工作流（误判核对 + 自校验）相比 legacy 单次调用的差异。
 *
 * @author Linson
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewEngineCompareService {

    private final ReviewAgent reviewAgent;
    private final ReviewGraphAgent reviewGraphAgent;
    private final ReviewTaskMapper taskMapper;

    /**
     * 对一批任务做双引擎对比。
     *
     * @param taskIds 待对比的任务 ID 列表
     * @return 每个任务的对比结果
     */
    public List<ReviewEngineCompareVo> compare(List<Long> taskIds) {
        List<ReviewEngineCompareVo> results = new ArrayList<>();
        for (Long taskId : taskIds) {
            results.add(compareOne(taskId));
        }
        return results;
    }

    private ReviewEngineCompareVo compareOne(Long taskId) {
        ReviewEngineCompareVo vo = new ReviewEngineCompareVo();
        vo.setTaskId(taskId);
        ReviewTask task = taskMapper.selectById(taskId);
        vo.setTaskName(task != null ? task.getTaskName() : "未知任务");
        vo.setHasError(false);

        try {
            // legacy 引擎
            long t1 = System.currentTimeMillis();
            JSONObject legacy = reviewAgent.executeDryRun(taskId);
            long legacyMs = System.currentTimeMillis() - t1;
            fillLegacy(vo, legacy, legacyMs);

            // graph 引擎
            long t2 = System.currentTimeMillis();
            JSONObject graph = reviewGraphAgent.executeDryRun(taskId);
            long graphMs = System.currentTimeMillis() - t2;
            fillGraph(vo, graph, graphMs);

            // 任一引擎报错
            if (legacy.containsKey("error") || graph.containsKey("error")) {
                vo.setHasError(true);
                vo.setErrorMessage(joinErrors(legacy, graph));
            }

            buildDiff(vo);
        } catch (Exception e) {
            log.error("[EngineCompare] 对比失败 taskId={}", taskId, e);
            vo.setHasError(true);
            vo.setErrorMessage("对比执行异常: " + e.getMessage());
        }
        return vo;
    }

    private void fillLegacy(ReviewEngineCompareVo vo, JSONObject r, long ms) {
        int[] c = countBySeverity(r);
        vo.setLegacyPassStatus(r.getString("pass_status"));
        vo.setLegacyItemCount(itemCount(r));
        vo.setLegacyErrorCount(c[0]);
        vo.setLegacyWarningCount(c[1]);
        vo.setLegacyInfoCount(c[2]);
        vo.setLegacyDurationMs(ms);
        vo.setLegacySummary(r.getString("summary"));
    }

    private void fillGraph(ReviewEngineCompareVo vo, JSONObject r, long ms) {
        int[] c = countBySeverity(r);
        vo.setGraphPassStatus(r.getString("pass_status"));
        vo.setGraphItemCount(itemCount(r));
        vo.setGraphErrorCount(c[0]);
        vo.setGraphWarningCount(c[1]);
        vo.setGraphInfoCount(c[2]);
        vo.setGraphDurationMs(ms);
        vo.setGraphSummary(r.getString("summary"));
        vo.setGraphReviewRound(r.getInteger("_reviewRound") != null ? r.getInteger("_reviewRound") : 1);
    }

    /** 统计 items 中 error/warning/info 各数量（matched 不计入），返回 [error, warning, info] */
    private int[] countBySeverity(JSONObject r) {
        int error = 0, warning = 0, info = 0;
        JSONArray items = r.getJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                JSONObject it = items.getJSONObject(i);
                if ("matched".equals(it.getString("match_status"))) {
                    continue;
                }
                String sev = it.getString("severity");
                if ("error".equals(sev)) {
                    error++;
                } else if ("warning".equals(sev)) {
                    warning++;
                } else if ("info".equals(sev)) {
                    info++;
                }
            }
        }
        return new int[]{error, warning, info};
    }

    private int itemCount(JSONObject r) {
        JSONArray items = r.getJSONArray("items");
        return items != null ? items.size() : 0;
    }

    private String joinErrors(JSONObject legacy, JSONObject graph) {
        StringBuilder sb = new StringBuilder();
        if (legacy.containsKey("error")) {
            sb.append("legacy: ").append(legacy.getString("error")).append("; ");
        }
        if (graph.containsKey("error")) {
            sb.append("graph: ").append(graph.getString("error"));
        }
        return sb.toString();
    }

    /** 构建文字差异摘要：通过结论是否变化、严重项增减、是否触发重审 */
    private void buildDiff(ReviewEngineCompareVo vo) {
        boolean passChanged = vo.getLegacyPassStatus() != null
            && !vo.getLegacyPassStatus().equals(vo.getGraphPassStatus());
        vo.setPassStatusChanged(passChanged);

        int errDelta = nz(vo.getLegacyErrorCount()) - nz(vo.getGraphErrorCount());
        vo.setErrorDelta(errDelta);

        StringBuilder sb = new StringBuilder();
        if (passChanged) {
            sb.append("通过结论变化: ").append(vo.getLegacyPassStatus())
                .append(" → ").append(vo.getGraphPassStatus()).append("; ");
        }
        if (errDelta > 0) {
            sb.append("graph 减少了 ").append(errDelta).append(" 条严重问题(误判核对/自校验降级); ");
        } else if (errDelta < 0) {
            sb.append("graph 多出 ").append(-errDelta).append(" 条严重问题; ");
        }
        if (vo.getGraphReviewRound() != null && vo.getGraphReviewRound() > 1) {
            sb.append("graph 触发了 ").append(vo.getGraphReviewRound()).append(" 轮自校验重审; ");
        }
        if (sb.length() == 0) {
            sb.append("两引擎结论一致");
        }
        vo.setDiffSummary(sb.toString().trim());
    }

    private int nz(Integer v) {
        return v != null ? v : 0;
    }
}

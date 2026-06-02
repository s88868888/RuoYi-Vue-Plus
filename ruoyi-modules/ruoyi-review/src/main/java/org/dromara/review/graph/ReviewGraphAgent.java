package org.dromara.review.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.service.AiChatService;
import org.dromara.review.agent.ReviewAgent;
import org.dromara.review.domain.ReviewTask;
import org.dromara.review.graph.node.FinalizeNode;
import org.dromara.review.graph.node.InitialReviewNode;
import org.dromara.review.graph.node.MisjudgeCrosscheckNode;
import org.dromara.review.graph.node.PrepareNode;
import org.dromara.review.graph.node.SelfVerifyNode;
import org.dromara.review.mapper.ReviewTaskFileMapper;
import org.dromara.review.mapper.ReviewTaskMapper;
import org.dromara.review.service.ReviewRagService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;

/**
 * 审核 Graph 工作流编排器（agentic 版审核入口）。
 * <p>
 * 把原本 {@link ReviewAgent#execute(Long)} 的单次调用流水线，重构为可循环、可条件路由的状态图：
 * <pre>
 *   START → prepare → initial_review → misjudge_crosscheck → self_verify
 *                          ↑                                      │
 *                          └──────── 需重审(依据不足) ────────────┘
 *                                                                 │ 校验通过
 *                                                                 ▼
 *                                                             finalize → END
 * </pre>
 * 关键设计：用同步 {@link CompiledGraph#invoke(Map)} 执行，所有节点留在调用线程，
 * 保留 RuoYi 的 TenantHelper(ThreadLocal) 租户上下文，与 legacy 行为一致。
 *
 * @author Linson
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewGraphAgent {

    private final ReviewAgent reviewAgent;
    private final ReviewTaskMapper taskMapper;
    private final ReviewTaskFileMapper taskFileMapper;
    private final ReviewRagService reviewRagService;
    private final AiChatService aiChatService;

    /**
     * state key 策略：CONTEXT 整体替换，MESSAGES 累积，NEXT 替换。
     */
    private KeyStrategyFactory keyStrategyFactory() {
        return () -> {
            Map<String, KeyStrategy> strategies = new HashMap<>();
            strategies.put(ReviewStateKeys.CONTEXT, KeyStrategy.REPLACE);
            strategies.put(ReviewStateKeys.NEXT, KeyStrategy.REPLACE);
            strategies.put(ReviewStateKeys.MESSAGES, KeyStrategy.APPEND);
            return strategies;
        };
    }

    /**
     * 构建并编译状态图。每次执行重新构建以保证无状态、线程安全。
     */
    private CompiledGraph buildGraph() throws Exception {
        AsyncNodeAction prepare = node_async(new PrepareNode(reviewAgent, taskMapper, taskFileMapper, reviewRagService));
        AsyncNodeAction initialReview = node_async(new InitialReviewNode(reviewAgent));
        AsyncNodeAction misjudgeCrosscheck = node_async(new MisjudgeCrosscheckNode(reviewRagService, aiChatService));
        AsyncNodeAction selfVerify = node_async(new SelfVerifyNode(aiChatService));
        AsyncNodeAction finalize = node_async(new FinalizeNode(reviewAgent));

        // self_verify 的条件路由：重审回 initial_review，否则去 finalize
        Map<String, String> verifyRoutes = new HashMap<>();
        verifyRoutes.put(ReviewStateKeys.ROUTE_REREVIEW, ReviewStateKeys.NODE_INITIAL_REVIEW);
        verifyRoutes.put(ReviewStateKeys.ROUTE_FINALIZE, ReviewStateKeys.NODE_FINALIZE);

        StateGraph graph = new StateGraph(keyStrategyFactory())
            .addNode(ReviewStateKeys.NODE_PREPARE, prepare)
            .addNode(ReviewStateKeys.NODE_INITIAL_REVIEW, initialReview)
            .addNode(ReviewStateKeys.NODE_MISJUDGE_CROSSCHECK, misjudgeCrosscheck)
            .addNode(ReviewStateKeys.NODE_SELF_VERIFY, selfVerify)
            .addNode(ReviewStateKeys.NODE_FINALIZE, finalize)
            .addEdge(START, ReviewStateKeys.NODE_PREPARE)
            .addEdge(ReviewStateKeys.NODE_PREPARE, ReviewStateKeys.NODE_INITIAL_REVIEW)
            .addEdge(ReviewStateKeys.NODE_INITIAL_REVIEW, ReviewStateKeys.NODE_MISJUDGE_CROSSCHECK)
            .addEdge(ReviewStateKeys.NODE_MISJUDGE_CROSSCHECK, ReviewStateKeys.NODE_SELF_VERIFY)
            .addConditionalEdges(ReviewStateKeys.NODE_SELF_VERIFY,
                edge_async(state -> ReviewGraphSupport.getContext(state) != null
                    ? state.<String>value(ReviewStateKeys.NEXT).orElse(ReviewStateKeys.ROUTE_FINALIZE)
                    : ReviewStateKeys.ROUTE_FINALIZE),
                verifyRoutes)
            .addEdge(ReviewStateKeys.NODE_FINALIZE, END);

        return graph.compile();
    }

    /**
     * 执行审核（agentic 工作流）。入参与 legacy {@link ReviewAgent#execute(Long)} 一致，便于灰度切换。
     *
     * @param taskId 审核任务 ID
     */
    public void execute(Long taskId) {
        ReviewTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("审核任务不存在: " + taskId);
        }
        log.info("[ReviewGraphAgent] 开始 Graph 审核: id={}, name={}, type={}",
            taskId, task.getTaskName(), task.getTaskType());

        ReviewContext ctx = new ReviewContext();
        ctx.setTask(task);
        ctx.setStartTimeMs(System.currentTimeMillis());

        try {
            CompiledGraph graph = buildGraph();
            Map<String, Object> input = new HashMap<>();
            input.put(ReviewStateKeys.CONTEXT, ctx);
            // 同步执行，节点全程留在当前线程，保留租户上下文
            graph.invoke(input);
        } catch (Exception e) {
            log.error("[ReviewGraphAgent] Graph 审核失败: taskId={}", taskId, e);
            task.setStatus("failed");
            task.setAiSummary("审核执行异常(graph): " + e.getMessage());
            taskMapper.updateById(task);
            try {
                reviewAgent.callbackExternalSystem(task);
            } catch (Exception ex) {
                log.warn("[ReviewGraphAgent] 失败回调异常 taskId={}", taskId, ex);
            }
        }
    }

    /**
     * dry-run 执行（评测用）：跑完整 agentic 工作流但不落库/不写知识库/不回调/不推进度，
     * 返回最终结果 JSON（含 items / pass_status / summary，已经过误判核对与自校验修正）。
     * 供双引擎对比端点调用。
     *
     * @param taskId 审核任务 ID
     * @return 最终审核结果 JSON；执行异常时返回带 error 字段的 JSON
     */
    public JSONObject executeDryRun(Long taskId) {
        ReviewTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("审核任务不存在: " + taskId);
        }
        log.info("[ReviewGraphAgent] 开始 Graph dry-run: taskId={}", taskId);

        ReviewContext ctx = new ReviewContext();
        ctx.setTask(task);
        ctx.setStartTimeMs(System.currentTimeMillis());
        ctx.setDryRun(true);

        try {
            CompiledGraph graph = buildGraph();
            Map<String, Object> input = new HashMap<>();
            input.put(ReviewStateKeys.CONTEXT, ctx);
            graph.invoke(input);
            JSONObject result = ctx.getReviewResult() != null ? ctx.getReviewResult() : new JSONObject();
            // 内部字段（下划线前缀）：供对比报告读取 graph 特有的轮次/误判命中信息
            result.put("_reviewRound", ctx.getReviewRound());
            result.put("_misjudgeHits", ctx.getMisjudgeHits().size());
            return result;
        } catch (Exception e) {
            log.error("[ReviewGraphAgent] Graph dry-run 失败: taskId={}", taskId, e);
            JSONObject err = new JSONObject();
            err.put("error", "graph dry-run 异常: " + e.getMessage());
            return err;
        }
    }
}

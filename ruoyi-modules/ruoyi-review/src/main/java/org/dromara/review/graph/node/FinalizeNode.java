package org.dromara.review.graph.node;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.review.agent.ReviewAgent;
import org.dromara.review.domain.ReviewTask;
import org.dromara.review.graph.ReviewContext;
import org.dromara.review.graph.ReviewGraphSupport;

import java.util.Map;

/**
 * 定稿节点：落库问题明细 → 更新任务状态/评分 → 沉淀知识案例 → 聚合问题模式 → 回调外部系统。
 * <p>
 * 复用 {@link ReviewAgent} 的 public 落库方法，与 legacy execute() 的步骤 7-10 一致。
 * 误判交叉核对与自校验已在上游节点完成，这里写入的是经过校正的最终结果。
 *
 * @author Linson
 */
@Slf4j
@RequiredArgsConstructor
public class FinalizeNode implements NodeAction {

    private final ReviewAgent reviewAgent;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        ReviewContext ctx = ReviewGraphSupport.getContext(state);
        ReviewTask task = ctx.getTask();
        JSONObject result = ctx.getReviewResult();
        Long taskId = task.getId();

        long duration = System.currentTimeMillis() - ctx.getStartTimeMs();

        // dry-run 评测：不落库/不写知识库/不回调，结果留在 ctx.reviewResult 供对比
        if (ctx.isDryRun()) {
            log.info("[Graph:finalize] dry-run 完成（不落库）: taskId={}, 轮次={}, 误判命中={}, 耗时={}ms",
                taskId, ctx.getReviewRound(), ctx.getMisjudgeHits().size(), duration);
            return ReviewGraphSupport.withMessage(ctx, "finalize(dry-run) 完成");
        }

        // 7. 落库明细 + 更新任务状态/评分
        reviewAgent.saveResultItems(task, result, ctx.getRules());
        reviewAgent.updateTaskStatus(task, result, duration, ctx.getRules(), ctx.getModelUsed(), ctx.getAiRawResponse());

        // 8. 沉淀知识案例
        reviewAgent.saveToKnowledgeCase(task, ctx.getStandardIds());

        // 9. 聚合问题模式
        reviewAgent.aggregatePatterns(task, ctx.getStandardIds());

        // 10. 回调外部系统（成功路径，失败不阻断）
        try {
            reviewAgent.callbackExternalSystem(task);
        } catch (Exception ex) {
            log.warn("[Graph:finalize] 成功路径回调失败 taskId={}", taskId, ex);
        }

        log.info("[Graph:finalize] 审核完成: taskId={}, passStatus={}, score={}, model={}, 轮次={}, 误判命中={}, 耗时={}ms",
            taskId, task.getPassStatus(), task.getScore(), ctx.getModelUsed(),
            ctx.getReviewRound(), ctx.getMisjudgeHits().size(), duration);

        return ReviewGraphSupport.withMessage(ctx, "finalize 完成: passStatus=" + task.getPassStatus());
    }
}

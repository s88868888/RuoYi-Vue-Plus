package org.dromara.review.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.sse.utils.SseMessageUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Graph 节点读写 {@link ReviewContext} 的工具方法，统一 state 存取，避免每个节点重复样板代码。
 *
 * @author Linson
 */
@Slf4j
public final class ReviewGraphSupport {

    private ReviewGraphSupport() {
    }

    /**
     * 从 state 取出共享上下文。理论上 prepare 之后一定存在；取不到说明编排出错，直接抛异常便于定位。
     */
    public static ReviewContext getContext(OverAllState state) {
        return state.<ReviewContext>value(ReviewStateKeys.CONTEXT)
            .orElseThrow(() -> new IllegalStateException("ReviewContext 未初始化，检查 Graph 初始 state 是否注入 " + ReviewStateKeys.CONTEXT));
    }

    /**
     * 节点返回值：把更新后的 context 与一条过程日志写回 state，并向任务发起人推送 SSE 进度。
     * CONTEXT 用 ReplaceStrategy 整体替换，MESSAGES 用 AppendStrategy 累积。
     */
    public static Map<String, Object> withMessage(ReviewContext ctx, String message) {
        pushProgress(ctx, message);
        Map<String, Object> patch = new HashMap<>(4);
        patch.put(ReviewStateKeys.CONTEXT, ctx);
        patch.put(ReviewStateKeys.MESSAGES, message);
        return patch;
    }

    /**
     * 节点返回值：同时声明下一跳路由 key（供条件边读取）。
     */
    public static Map<String, Object> withRoute(ReviewContext ctx, String message, String nextRoute) {
        Map<String, Object> patch = withMessage(ctx, message);
        patch.put(ReviewStateKeys.NEXT, nextRoute);
        return patch;
    }

    /**
     * 向任务发起人推送审核进度（SSE）。dry-run 评测不推；SSE 未启用 / 无发起人 / 推送异常均静默跳过，
     * 进度推送绝不能影响审核主流程。
     */
    private static void pushProgress(ReviewContext ctx, String message) {
        try {
            if (ctx == null || ctx.isDryRun() || ctx.getTask() == null) {
                return;
            }
            Long userId = ctx.getTask().getCreateBy();
            if (userId == null || !SseMessageUtils.isEnable()) {
                return;
            }
            JSONObject payload = new JSONObject();
            payload.put("type", "review_progress");
            payload.put("taskId", ctx.getTask().getId());
            payload.put("round", ctx.getReviewRound());
            payload.put("message", message);
            SseMessageUtils.sendMessage(userId, payload.toJSONString());
        } catch (Exception e) {
            log.debug("[ReviewGraphSupport] 进度 SSE 推送失败（不影响审核）: {}", e.getMessage());
        }
    }
}

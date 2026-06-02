package org.dromara.review.graph.node;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.service.AiChatService;
import org.dromara.review.graph.ReviewContext;
import org.dromara.review.graph.ReviewGraphSupport;
import org.dromara.review.graph.ReviewStateKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自校验节点（核心增量 2）。
 * <p>
 * 对误判核对后剩余的高风险问题项（error）做一次"反思复核"：让模型站在质检角度质疑每条判定的
 * <b>原文依据是否充分</b>。把依据不足、疑似武断的项收集为疑点 {@link ReviewContext#getVerifyDoubts()}。
 * <p>
 * 之后由 {@link #shouldReReview} 决定路由：
 * <ul>
 *   <li>有疑点 且 未超过最大轮次 → 路由回 initial_review 带着疑点重审（自我纠错循环）</li>
 *   <li>无疑点 或 已达最大轮次 → 路由到 finalize 定稿</li>
 * </ul>
 *
 * @author Linson
 */
@Slf4j
@RequiredArgsConstructor
public class SelfVerifyNode implements NodeAction {

    private final AiChatService aiChatService;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        ReviewContext ctx = ReviewGraphSupport.getContext(state);
        JSONObject result = ctx.getReviewResult();
        JSONArray items = result != null ? result.getJSONArray("items") : null;

        // 清空上一轮疑点，重新评估本轮结果
        ctx.setVerifyDoubts(new ArrayList<>());

        List<JSONObject> highRisk = collectHighRisk(items);
        if (highRisk.isEmpty()) {
            log.info("[Graph:self_verify] 无高风险项，直接定稿");
            return ReviewGraphSupport.withRoute(ctx, "self_verify: 无高风险项，定稿",
                ReviewStateKeys.ROUTE_FINALIZE);
        }

        List<String> doubts = verifyHighRiskItems(highRisk, ctx);
        ctx.setVerifyDoubts(doubts);

        boolean reReview = shouldReReview(ctx, doubts);
        if (reReview) {
            ctx.setReviewRound(ctx.getReviewRound() + 1);
            log.info("[Graph:self_verify] 发现 {} 条依据不足，触发第 {} 轮重审", doubts.size(), ctx.getReviewRound());
            return ReviewGraphSupport.withRoute(ctx,
                "self_verify: 疑点" + doubts.size() + "条，重审第" + ctx.getReviewRound() + "轮",
                ReviewStateKeys.ROUTE_REREVIEW);
        }

        String msg = doubts.isEmpty()
            ? "self_verify: 高风险项依据充分，定稿"
            : "self_verify: 仍有" + doubts.size() + "条疑点但已达最大轮次，定稿(保留疑点提示)";
        log.info("[Graph:self_verify] {}", msg);
        return ReviewGraphSupport.withRoute(ctx, msg, ReviewStateKeys.ROUTE_FINALIZE);
    }

    /** 收集 error 级、且未被误判核对降级的高风险项 */
    private List<JSONObject> collectHighRisk(JSONArray items) {
        List<JSONObject> list = new ArrayList<>();
        if (items == null) {
            return list;
        }
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            if ("error".equals(item.getString("severity"))
                && !Boolean.TRUE.equals(item.getBoolean("misjudged_hint"))) {
                list.add(item);
            }
        }
        return list;
    }

    /** 是否需要重审：有疑点 且 当前轮次未达上限 */
    private boolean shouldReReview(ReviewContext ctx, List<String> doubts) {
        return !doubts.isEmpty() && ctx.getReviewRound() < ReviewStateKeys.MAX_REVIEW_ROUND;
    }

    /**
     * 让模型对高风险项做反思复核，返回"依据不足/疑似武断"的疑点列表。
     * 复核基于表单原文（formSnapshot），质疑每条判定能否从原文找到充分支撑。
     * 模型按行返回 "字段名|疑点说明"，无疑点返回 PASS。
     */
    private List<String> verifyHighRiskItems(List<JSONObject> highRisk, ReviewContext ctx) {
        List<String> doubts = new ArrayList<>();

        StringBuilder judgments = new StringBuilder();
        for (int i = 0; i < highRisk.size(); i++) {
            JSONObject item = highRisk.get(i);
            judgments.append(i + 1).append(". 字段[").append(item.getString("field_name")).append("] ")
                .append(item.getString("description")).append("\n");
        }

        String formText = ctx.getTask().getFormSnapshot() != null ? ctx.getTask().getFormSnapshot() : "{}";
        String system = "你是严谨的审核质检员，负责复核 AI 审核员给出的【不通过/严重】判定是否站得住脚。"
            + "对每条判定，检查能否从表单原文中找到充分依据。"
            + "若某条判定依据不足、过度推断或与原文矛盾，输出一行：字段名|具体疑点。"
            + "所有判定都依据充分则只输出：PASS。不要输出多余内容。";
        String user = "【表单原文】\n" + formText + "\n\n【待复核的高风险判定】\n" + judgments
            + "\n请逐条复核，输出存疑项（字段名|疑点），无存疑输出 PASS。";

        try {
            String answer;
            if (ctx.getModelConfig() != null) {
                answer = aiChatService.chatWithConfig(ctx.getModelConfig(), system, user);
            } else {
                answer = aiChatService.chat(system, user);
            }
            if (answer == null || answer.trim().toUpperCase().startsWith("PASS")) {
                return doubts;
            }
            for (String line : answer.split("\\r?\\n")) {
                String t = line.trim();
                if (t.isEmpty() || t.toUpperCase().startsWith("PASS")) {
                    continue;
                }
                // 去掉可能的序号前缀
                t = t.replaceFirst("^\\d+[.、)]\\s*", "");
                if (!t.isEmpty()) {
                    doubts.add(t);
                }
            }
        } catch (Exception e) {
            log.warn("[Graph:self_verify] 自校验调用失败，本轮不触发重审: {}", e.getMessage());
        }
        return doubts;
    }
}

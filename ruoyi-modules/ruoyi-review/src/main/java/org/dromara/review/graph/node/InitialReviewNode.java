package org.dromara.review.graph.node;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.review.agent.ReviewAgent;
import org.dromara.review.graph.ReviewContext;
import org.dromara.review.graph.ReviewGraphSupport;

import java.util.Map;

/**
 * 初审节点：组装 prompt → 调 AI → 解析 JSON，产出问题项草稿，存入 {@link ReviewContext#getReviewResult()}。
 * <p>
 * 该节点可被 self_verify 的条件边路由回来重审（reviewRound 递增）。重审时把上一轮自校验提出的疑点
 * 追加到 system prompt 末尾，引导模型重新审视，形成有限次自我纠错。
 *
 * @author Linson
 */
@Slf4j
@RequiredArgsConstructor
public class InitialReviewNode implements NodeAction {

    private final ReviewAgent reviewAgent;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        ReviewContext ctx = ReviewGraphSupport.getContext(state);
        int round = ctx.getReviewRound();

        log.info("[Graph:initial_review] 第 {} 轮审核开始: taskId={}", round, ctx.getTask().getId());

        String systemPrompt = reviewAgent.buildSystemPrompt(
            ctx.getTemplate(), ctx.getRulesText(), ctx.getKnowledgeContext(), ctx.getRules());

        // 重审：把上一轮自校验的疑点拼进 system prompt，引导模型针对性复核
        if (round > 1 && !ctx.getVerifyDoubts().isEmpty()) {
            StringBuilder sb = new StringBuilder(systemPrompt);
            sb.append("\n\n=== 上一轮自校验发现以下疑点，请重点复核并修正 ===\n");
            for (String d : ctx.getVerifyDoubts()) {
                sb.append("- ").append(d).append("\n");
            }
            sb.append("请基于这些疑点重新审核，对依据不足的判定下调严重程度或标记 uncertain，不要凭空臆断。\n");
            systemPrompt = sb.toString();
        }

        String userPrompt = reviewAgent.buildUserPrompt(ctx.getTemplate(), ctx.getTask());

        // 文档预处理（OCR/抽文）结果缓存：首轮现做并存入 context，重审复用，避免重复 OCR
        if (ctx.getPreprocessedDocText() == null) {
            ctx.setPreprocessedDocText(reviewAgent.preprocessDocuments(ctx.getFiles(), ctx.getTemplate()));
        }
        // 缓存为空串表示无文档（图片/纯表单场景），传 null 让 callAi 走非文档分支
        String cachedDocText = (ctx.getPreprocessedDocText() == null || ctx.getPreprocessedDocText().isEmpty())
            ? null : ctx.getPreprocessedDocText();

        String aiResponse = reviewAgent.callAi(
            ctx.getFiles(), systemPrompt, userPrompt, ctx.getTemplate(), ctx.getModelConfig(), cachedDocText);
        log.info("[Graph:initial_review] 第 {} 轮 AI 返回长度: {}", round, aiResponse.length());

        JSONObject result = reviewAgent.parseAiResponse(aiResponse);
        ctx.setAiRawResponse(aiResponse);
        ctx.setReviewResult(result);
        ctx.setModelUsed(reviewAgent.determineModel(ctx.getFiles(), ctx.getTemplate(), ctx.getModelConfig()));

        int itemCount = result.getJSONArray("items") != null ? result.getJSONArray("items").size() : 0;
        return ReviewGraphSupport.withMessage(ctx,
            "initial_review 第" + round + "轮: 产出问题项 " + itemCount);
    }
}

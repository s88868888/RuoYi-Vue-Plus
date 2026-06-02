package org.dromara.review.graph.node;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.domain.VectorSearchResult;
import org.dromara.common.ai.service.AiChatService;
import org.dromara.review.graph.ReviewContext;
import org.dromara.review.graph.ReviewGraphSupport;
import org.dromara.review.service.ReviewRagService;

import java.util.List;
import java.util.Map;

/**
 * 误判交叉核对节点（核心增量 1）。
 * <p>
 * legacy 流程里误判库只在开审前做一次泛化 RAG 检索当背景；本节点改为：初审产出问题项后，
 * <b>对每条判为"有问题"的项主动回查相似历史误判</b>，命中则让模型做一次轻量判断"当前判定是否
 * 疑似误判"，确认疑似则下调严重程度 / 标记 misjudged，并记录命中说明。
 * <p>
 * 这正是用户期望的"根据错误自主寻找误判经验对比"。
 *
 * @author Linson
 */
@Slf4j
@RequiredArgsConstructor
public class MisjudgeCrosscheckNode implements NodeAction {

    private final ReviewRagService reviewRagService;
    private final AiChatService aiChatService;

    /** 单条问题项召回的相似误判条数 */
    private static final int TOP_K = 3;

    @Override
    public Map<String, Object> apply(OverAllState state) {
        ReviewContext ctx = ReviewGraphSupport.getContext(state);
        JSONObject result = ctx.getReviewResult();
        JSONArray items = result != null ? result.getJSONArray("items") : null;

        if (items == null || items.isEmpty()) {
            log.info("[Graph:misjudge_crosscheck] 无问题项，跳过误判核对");
            return ReviewGraphSupport.withMessage(ctx, "misjudge_crosscheck: 无问题项");
        }

        int hitCount = 0;
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (!isProblemItem(item)) {
                continue;
            }
            String queryText = buildQuery(item);
            List<VectorSearchResult> similar = reviewRagService.searchMisjudgments(
                ctx.getStandardIds(), queryText, TOP_K);
            if (similar.isEmpty()) {
                continue;
            }

            // 命中相似误判 → 让模型判断当前判定是否疑似误判
            if (judgeIsMisjudgment(item, similar, ctx)) {
                downgrade(item);
                String note = "字段[" + item.getString("field_name") + "]命中历史误判经验，已降级为 uncertain";
                ctx.getMisjudgeHits().add(note);
                hitCount++;
                log.info("[Graph:misjudge_crosscheck] {}", note);
            }
        }

        return ReviewGraphSupport.withMessage(ctx,
            "misjudge_crosscheck: 核对问题项，命中误判 " + hitCount + " 条");
    }

    /** 是否为"有问题"的项：match_status 非 matched，或 severity 为 error/warning */
    private boolean isProblemItem(JSONObject item) {
        String status = item.getString("match_status");
        String severity = item.getString("severity");
        if ("matched".equals(status)) {
            return false;
        }
        return "error".equals(severity) || "warning".equals(severity)
            || (status != null && !"matched".equals(status));
    }

    /** 用字段名 + 描述构建语义召回 query */
    private String buildQuery(JSONObject item) {
        StringBuilder sb = new StringBuilder();
        if (item.getString("field_name") != null) {
            sb.append(item.getString("field_name")).append(" ");
        }
        if (item.getString("field_label") != null) {
            sb.append(item.getString("field_label")).append(" ");
        }
        if (item.getString("description") != null) {
            sb.append(item.getString("description"));
        }
        return sb.toString().trim();
    }

    /** 把疑似误判的问题项降级：severity→info，match_status→uncertain，标记 misjudged */
    private void downgrade(JSONObject item) {
        item.put("severity", "info");
        item.put("match_status", "uncertain");
        item.put("misjudged_hint", true);
        String suggestion = item.getString("suggestion");
        item.put("suggestion", (suggestion != null ? suggestion + " " : "")
            + "[系统提示] 该判定与历史误判经验相似，建议人工复核后再定性。");
    }

    /**
     * 让模型判断"当前问题项判定"是否与召回的历史误判一致（即疑似误判）。
     * 轻量纯文本调用，要求只回 YES/NO，保守起见解析失败按"非误判"处理（不误降级真实问题）。
     */
    private boolean judgeIsMisjudgment(JSONObject item, List<VectorSearchResult> similar, ReviewContext ctx) {
        StringBuilder exp = new StringBuilder();
        for (int i = 0; i < similar.size(); i++) {
            exp.append(i + 1).append(". ").append(similar.get(i).getContent()).append("\n");
        }
        String system = "你是审核质检员。下面给出一条 AI 审核判定，以及历史上人工纠正过的相似误判经验。"
            + "请判断：当前这条判定是否很可能也是误判（即 AI 判错了、实际应通过或不该这么严重）。"
            + "只回答 YES 或 NO，不要解释。判断不确定时回答 NO。";
        String user = "【当前判定】\n"
            + "字段: " + item.getString("field_name") + "\n"
            + "判定理由: " + item.getString("description") + "\n"
            + "严重程度: " + item.getString("severity") + "\n\n"
            + "【历史相似误判经验】\n" + exp + "\n"
            + "当前判定是否疑似误判？(YES/NO)";
        try {
            String answer;
            if (ctx.getModelConfig() != null) {
                answer = aiChatService.chatWithConfig(ctx.getModelConfig(), system, user);
            } else {
                answer = aiChatService.chat(system, user);
            }
            boolean yes = answer != null && answer.trim().toUpperCase().contains("YES");
            log.debug("[Graph:misjudge_crosscheck] 字段[{}] 误判判断={}", item.getString("field_name"), yes);
            return yes;
        } catch (Exception e) {
            log.warn("[Graph:misjudge_crosscheck] 误判判断调用失败，保守判为非误判: {}", e.getMessage());
            return false;
        }
    }
}

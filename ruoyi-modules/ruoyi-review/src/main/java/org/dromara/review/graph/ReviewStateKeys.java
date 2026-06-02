package org.dromara.review.graph;

/**
 * 审核 Graph 工作流的 state key 与节点名常量。
 *
 * @author Linson
 */
public final class ReviewStateKeys {

    private ReviewStateKeys() {
    }

    // ==================== state keys ====================

    /** 跨节点共享的强类型载体 {@link ReviewContext}（ReplaceStrategy 整体替换） */
    public static final String CONTEXT = "review_context";

    /** 条件路由声明：下一个目标节点的逻辑名 */
    public static final String NEXT = "next_node";

    /** 过程日志（AppendStrategy 累积，便于观测每步发生了什么） */
    public static final String MESSAGES = "messages";

    // ==================== 节点名 ====================

    public static final String NODE_PREPARE = "prepare";
    public static final String NODE_INITIAL_REVIEW = "initial_review";
    public static final String NODE_MISJUDGE_CROSSCHECK = "misjudge_crosscheck";
    public static final String NODE_SELF_VERIFY = "self_verify";
    public static final String NODE_FINALIZE = "finalize";

    // ==================== 路由 key（self_verify 条件边用） ====================

    /** 需要重审：回到 initial_review */
    public static final String ROUTE_REREVIEW = "re_review";
    /** 校验通过：进入 finalize 定稿 */
    public static final String ROUTE_FINALIZE = "to_finalize";

    /** 自校验触发重审的最大轮次（含初审），超过则强制定稿，防止死循环 */
    public static final int MAX_REVIEW_ROUND = 2;
}

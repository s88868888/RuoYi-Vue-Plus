package org.dromara.review.domain.vo;

import lombok.Data;

/**
 * 双引擎(legacy vs graph)审核对比结果 VO。
 * <p>
 * 对同一审核任务分别用两种引擎 dry-run（只算不落库），提取关键指标做对比，
 * 用于评估 graph agentic 工作流（误判核对 + 自校验）相比 legacy 单次调用的差异。
 *
 * @author Linson
 */
@Data
public class ReviewEngineCompareVo {

    /** 任务ID */
    private Long taskId;

    /** 任务名称 */
    private String taskName;

    // ===== legacy 引擎结果 =====
    private String legacyPassStatus;
    private Integer legacyItemCount;
    private Integer legacyErrorCount;
    private Integer legacyWarningCount;
    private Integer legacyInfoCount;
    private Long legacyDurationMs;
    private String legacySummary;

    // ===== graph 引擎结果 =====
    private String graphPassStatus;
    private Integer graphItemCount;
    private Integer graphErrorCount;
    private Integer graphWarningCount;
    private Integer graphInfoCount;
    private Long graphDurationMs;
    private String graphSummary;

    /** graph 相比 legacy 的轮次（>1 表示触发了自校验重审） */
    private Integer graphReviewRound;

    /** 通过结论是否不同（legacy 与 graph 的 pass_status 不一致） */
    private Boolean passStatusChanged;

    /** graph 相比 legacy 减少的"严重"问题数（正数=graph 更宽松，多为误判核对/自校验降级） */
    private Integer errorDelta;

    /** 文字差异摘要 */
    private String diffSummary;

    /** 是否有引擎执行出错 */
    private Boolean hasError;
    private String errorMessage;
}

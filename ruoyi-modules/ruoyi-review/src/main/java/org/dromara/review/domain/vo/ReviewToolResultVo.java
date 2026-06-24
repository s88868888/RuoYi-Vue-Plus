package org.dromara.review.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * AI 审核工具结果VO（附件对比 COMPARE / 内容审查 AUDIT）。
 * <p>
 * 聚合 review_task + review_task_file + review_result_item + review_standard_focus，
 * 形状对齐城更 AiReviewResultVo，供前端「附件对比 / 内容审查」查看器直接消费。
 *
 * @author Linson
 * @date 2026-06-23
 */
@Data
public class ReviewToolResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务ID（字符串，前端轮询用） */
    private String id;

    /** 审核任务ID（同 id 的 Long 形态） */
    private Long reviewTaskId;

    /** 审核类型 COMPARE 双文档对比 / AUDIT 单文档内容审查（前端据此决定打开哪个查看器） */
    private String reviewtype;

    /** 状态 PENDING/RUNNING/SUCCESS/FAIL（已归一引擎的 pending/reviewing/completed/failed） */
    private String status;

    /** 通过状态 */
    private String passStatus;

    private Integer score;
    private Integer errorCount;
    private Integer warningCount;
    private Integer infoCount;
    /** 问题总数（前端角标） */
    private Integer totalIssues;

    private String aiSummary;

    /** 打印协议PDF（输入A，COMPARE 的基准文件） */
    private String printPdfUrl;

    /** 线下签字附件（输入B：COMPARE 的对比文件 / AUDIT 的被审查文档） */
    private String signFileUrl;

    /** B 侧附件原始文件名（内容审查标题展示用） */
    private String signFileName;

    /** A 侧 searchable PDF（OCR 后可搜索版，缺失时前端回退 printPdfUrl） */
    private String printSearchableUrl;

    /** B 侧 searchable PDF（缺失时前端回退 signFileUrl） */
    private String signSearchableUrl;

    /** B 侧附件 ossId（前端轮询 OCR 状态用） */
    private Long signOssId;

    /** B 侧 OCR 状态 NONE/SKIP/PENDING/RUNNING/SUCCESS/FAIL */
    private String signOcrStatus;

    private String errorMsg;

    private Date createtime;
    private Date updatetime;

    /** 附件对比差异清单批注（JSON：签名→批注内容，COMPARE 专用） */
    private String noteData;

    /** 脱敏手动框选数据（JSON，内容审查专用） */
    private String redactData;

    /** 问题明细列表 */
    private List<IssueItem> issues;

    /** 关注列表（AI 提取的各分类文本片段，非问题项） */
    private List<FocusItem> focusItems;

    /** 关注分类列表（前端生成过滤按钮/缺漏占位用） */
    private List<String> focusCategories;

    /** 关注要点列表（所选标准 review_standard_focus 启用项，前端按要点生成关注列表与缺漏占位） */
    private List<FocusKeyword> focusKeywords;

    @Data
    public static class FocusKeyword {
        /** 关注要点原文（与 FocusItem.keyword 关联） */
        private String keyword;
        /** 该要点所属分类（可空） */
        private String category;
    }

    @Data
    public static class IssueItem {
        /** 字段名称 */
        private String fieldName;
        /** 字段标签（用户友好） */
        private String fieldLabel;
        /** 打印协议中的值 / 系统标准值 */
        private String formValue;
        /** 线下签字附件中的值 / 文档提取值 */
        private String extractedValue;
        /** 匹配状态 matched/mismatched/not_found/uncertain 或 PASS/FAIL */
        private String matchStatus;
        /** 严重程度 error/warning/info（引擎原生小写，前端配 Tag 色） */
        private String severity;
        /** 置信度 */
        private BigDecimal confidence;
        /** 定位信息 */
        private String location;
        /** 问题描述 */
        private String description;
        /** 修改建议 */
        private String suggestion;
        /** 命中的规则原文（review_standard_rule.content，前端「规则说明」展示） */
        private String ruleContent;
        /** 命中的检查方法（review_standard_rule.check_method） */
        private String checkMethod;
        /** 用户批注（前端清单可编辑并保存，导出批注时写入PDF） */
        private String note;
    }

    @Data
    public static class FocusItem {
        /** 关注要点原文（与规则 focusKeyword 关联，前端按要点逐条定位） */
        private String keyword;
        /** 规则分类（对应 review_rule_category 字典值） */
        private String category;
        /** 字段标签（用户友好） */
        private String fieldLabel;
        /** 从文档提取的文本值 */
        private String extractedValue;
        /** 定位信息（页码/段落） */
        private String location;
        /** 置信度 */
        private BigDecimal confidence;
    }
}

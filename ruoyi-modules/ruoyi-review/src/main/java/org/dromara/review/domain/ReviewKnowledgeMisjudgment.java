package org.dromara.review.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 审核知识库误判记录对象 review_knowledge_misjudgment
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("review_knowledge_misjudgment")
public class ReviewKnowledgeMisjudgment extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 知识库ID
     */
    private Long knowledgeId;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 规则ID
     */
    private Long ruleId;

    /**
     * 字段名称
     */
    private String fieldName;

    /**
     * AI判断结果
     */
    private String aiJudgment;

    /**
     * 正确判断结果
     */
    private String correctJudgment;

    /**
     * 误判原因
     */
    private String reason;

    /**
     * 是否已学习
     */
    private String isLearned;

}

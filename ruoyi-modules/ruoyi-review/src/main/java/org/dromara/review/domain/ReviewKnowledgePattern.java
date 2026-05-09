package org.dromara.review.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 审核知识库问题模式对象 review_knowledge_pattern
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("review_knowledge_pattern")
public class ReviewKnowledgePattern extends TenantEntity {

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
     * 模式名称
     */
    private String patternName;

    /**
     * 模式描述
     */
    private String description;

    /**
     * 出现频率
     */
    private Integer frequency;

    /**
     * 准确率
     */
    private BigDecimal accuracy;

    /**
     * 解决方案
     */
    private String solution;

}

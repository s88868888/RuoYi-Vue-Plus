package org.dromara.review.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 审核知识库对象 review_knowledge
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("review_knowledge")
public class ReviewKnowledge extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 知识库名称
     */
    private String name;

    /**
     * 知识库类型
     */
    private String type;

    /**
     * 知识库描述
     */
    private String description;

    /**
     * 文档数量
     */
    private Integer docCount;

    /**
     * 案例数量
     */
    private Integer caseCount;

    /**
     * 模式数量
     */
    private Integer patternCount;

    /**
     * 准确率
     */
    private BigDecimal accuracy;

    /**
     * 状态
     */
    private String status;

    /**
     * 向量集合
     */
    private String vectorCollection;

    /**
     * 备注
     */
    private String remark;

}

package org.dromara.review.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 审核标准规则对象 review_standard_rule
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("review_standard_rule")
public class ReviewStandardRule extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 标准ID
     */
    private Long standardId;

    /**
     * 规则内容
     */
    private String content;

    /**
     * 规则分类
     */
    private String category;

    /**
     * 严重程度
     */
    private String severity;

    /**
     * 检查字段
     */
    private String checkField;

    /**
     * 检查方法
     */
    private String checkMethod;

    /**
     * 权重
     */
    private Integer weight;

    /**
     * 置信度
     */
    private BigDecimal confidence;

    /**
     * 命中次数
     */
    private Integer hitCount;

    /**
     * 未命中次数
     */
    private Integer missCount;

    /**
     * 排序顺序
     */
    private Integer sortOrder;

    /**
     * 状态
     */
    private String status;

}

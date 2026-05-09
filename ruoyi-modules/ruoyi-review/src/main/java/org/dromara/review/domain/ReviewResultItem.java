package org.dromara.review.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 审核结果明细对象 review_result_item
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("review_result_item")
public class ReviewResultItem extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 字段名称
     */
    private String fieldName;

    /**
     * 字段标签
     */
    private String fieldLabel;

    /**
     * 表单值
     */
    private String formValue;

    /**
     * 提取值
     */
    private String extractedValue;

    /**
     * 匹配状态
     */
    private String matchStatus;

    /**
     * 置信度
     */
    private BigDecimal confidence;

    /**
     * 严重程度
     */
    private String severity;

    /**
     * 规则ID
     */
    private Long ruleId;

    /**
     * 定位信息
     */
    private String location;

    /**
     * 问题描述
     */
    private String description;

    /**
     * 修改建议
     */
    private String suggestion;

    /**
     * AI备注
     */
    private String aiRemark;

    /**
     * 是否误判
     */
    private String misjudged;

    /**
     * 误判原因
     */
    private String misjudgmentReason;

    /**
     * 段落ID
     */
    private Long paragraphId;

    /**
     * 排序顺序
     */
    private Integer sortOrder;

}

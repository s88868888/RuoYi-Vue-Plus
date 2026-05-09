package org.dromara.review.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.review.domain.ReviewStandardRule;

/**
 * 审核标准规则业务对象 review_standard_rule
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ReviewStandardRule.class, reverseConvertGenerate = false)
public class ReviewStandardRuleBo extends BaseEntity {

    /**
     * 主键ID
     */
    @NotNull(message = "主键不能为空", groups = {EditGroup.class})
    private Long id;

    /**
     * 标准ID
     */
    @NotNull(message = "标准ID不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long standardId;

    /**
     * 规则内容
     */
    @NotBlank(message = "规则内容不能为空", groups = {AddGroup.class, EditGroup.class})
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
     * 排序顺序
     */
    private Integer sortOrder;

    /**
     * 状态
     */
    private String status;

}

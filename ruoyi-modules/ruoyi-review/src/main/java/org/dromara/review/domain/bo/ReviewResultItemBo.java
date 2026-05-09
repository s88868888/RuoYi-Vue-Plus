package org.dromara.review.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.review.domain.ReviewResultItem;

/**
 * 审核结果明细业务对象 review_result_item
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ReviewResultItem.class, reverseConvertGenerate = false)
public class ReviewResultItemBo extends BaseEntity {

    /**
     * 主键ID
     */
    @NotNull(message = "主键不能为空", groups = {EditGroup.class})
    private Long id;

    /**
     * 任务ID
     */
    @NotNull(message = "任务ID不能为空", groups = {AddGroup.class, EditGroup.class})
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
     * 严重程度
     */
    private String severity;

    /**
     * 是否误判
     */
    private String misjudged;

    /**
     * 误判原因
     */
    private String misjudgmentReason;

}

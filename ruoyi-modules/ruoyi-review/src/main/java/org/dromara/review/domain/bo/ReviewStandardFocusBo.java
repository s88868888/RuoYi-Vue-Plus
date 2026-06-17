package org.dromara.review.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.review.domain.ReviewStandardFocus;

/**
 * 审核标准关注要点业务对象 review_standard_focus
 *
 * @author ruoyi
 * @date 2026-06-17
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ReviewStandardFocus.class, reverseConvertGenerate = false)
public class ReviewStandardFocusBo extends BaseEntity {

    /**
     * 主键ID
     */
    @NotNull(message = "主键不能为空", groups = {EditGroup.class})
    private Long id;

    /**
     * 所属标准ID
     */
    @NotNull(message = "标准ID不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long standardId;

    /**
     * 关注要点
     */
    @NotBlank(message = "关注要点不能为空", groups = {AddGroup.class, EditGroup.class})
    private String keyword;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 状态（0=启用 1=停用）
     */
    private String status;

}

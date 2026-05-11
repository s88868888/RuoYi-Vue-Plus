package org.dromara.review.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.review.domain.ReviewStandard;

/**
 * 审核标准业务对象 review_standard
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ReviewStandard.class, reverseConvertGenerate = false)
public class ReviewStandardBo extends BaseEntity {

    /**
     * 主键ID
     */
    @NotNull(message = "主键不能为空", groups = {EditGroup.class})
    private Long id;

    /**
     * 标准名称
     */
    @NotBlank(message = "标准名称不能为空", groups = {AddGroup.class, EditGroup.class})
    private String name;

    /**
     * 标准类型
     */
    private String type;

    /**
     * 版本号
     */
    private String version;

    /**
     * 描述
     */
    private String description;

    /**
     * 是否系统内置
     */
    private String isSystem;

    /**
     * 状态
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

}

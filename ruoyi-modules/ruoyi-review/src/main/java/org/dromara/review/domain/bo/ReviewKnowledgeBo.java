package org.dromara.review.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.review.domain.ReviewKnowledge;

/**
 * 审核知识库业务对象 review_knowledge
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ReviewKnowledge.class, reverseConvertGenerate = false)
public class ReviewKnowledgeBo extends BaseEntity {

    /**
     * 主键ID
     */
    @NotNull(message = "主键不能为空", groups = {EditGroup.class})
    private Long id;

    /**
     * 知识库名称
     */
    @NotBlank(message = "知识库名称不能为空", groups = {AddGroup.class, EditGroup.class})
    private String name;

    /**
     * 知识库类型
     */
    @NotBlank(message = "知识库类型不能为空", groups = {AddGroup.class, EditGroup.class})
    private String type;

    /**
     * 知识库描述
     */
    private String description;

    /**
     * 状态
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

}

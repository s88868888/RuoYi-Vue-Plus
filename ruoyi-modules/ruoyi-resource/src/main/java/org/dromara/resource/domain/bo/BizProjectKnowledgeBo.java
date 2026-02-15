package org.dromara.resource.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.resource.domain.BizProjectKnowledge;

/**
 * 项目知识业务对象 biz_project_knowledge
 *
 * @author ruoyi
 * @date 2026-02-12
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BizProjectKnowledge.class, reverseConvertGenerate = false)
public class BizProjectKnowledgeBo extends BaseEntity {

    /**
     * 主键ID
     */
    @NotNull(message = "主键ID不能为空", groups = {EditGroup.class})
    private Long id;

    /**
     * 关联部门ID
     */
    private Long deptId;

    /**
     * 知识名称
     */
    @NotBlank(message = "知识名称不能为空", groups = {AddGroup.class, EditGroup.class})
    private String knowledgeName;

    /**
     * 描述
     */
    private String description;

    /**
     * 挂标项目类型
     */
    @NotBlank(message = "挂标项目类型不能为空", groups = {AddGroup.class, EditGroup.class})
    private String projectType;

    /**
     * 数据权限类型（0私域 1公域）
     */
    private String dataPermissionType;

    /**
     * 附件URL
     */
    @NotBlank(message = "附件不能为空", groups = {AddGroup.class, EditGroup.class})
    private String attachmentUrl;

    /**
     * 附件名称
     */
    private String attachmentName;

    /**
     * 备注
     */
    private String remark;

}

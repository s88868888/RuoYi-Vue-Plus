package org.dromara.resource.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.dromara.resource.domain.BizProjectKnowledge;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 项目知识视图对象 biz_project_knowledge
 *
 * @author ruoyi
 * @date 2026-02-12
 */
@Data
@AutoMapper(target = BizProjectKnowledge.class)
public class BizProjectKnowledgeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "关联部门ID")
    private Long deptId;

    @Schema(description = "知识名称")
    private String knowledgeName;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "挂标项目类型")
    private String projectType;

    @Schema(description = "数据权限类型（0私域 1公域）")
    private String dataPermissionType;

    @Schema(description = "附件URL")
    private String attachmentUrl;

    @Schema(description = "附件名称")
    private String attachmentName;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "创建人")
    private String createByName;

}

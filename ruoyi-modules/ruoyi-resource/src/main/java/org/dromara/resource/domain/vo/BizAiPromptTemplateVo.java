package org.dromara.resource.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.dromara.resource.domain.BizAiPromptTemplate;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * AI提示词模板视图对象
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Data
@AutoMapper(target = BizAiPromptTemplate.class)
public class BizAiPromptTemplateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "关联部门ID")
    private Long deptId;

    @Schema(description = "模板名称")
    private String templateName;

    @Schema(description = "模板类型")
    private String templateType;

    @Schema(description = "提示词内容")
    private String promptContent;

    @Schema(description = "是否系统模板")
    private String isSystem;

    @Schema(description = "排序号")
    private Integer sortOrder;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建人")
    private Long createBy;

    @Schema(description = "创建时间")
    private Date createTime;

}

package org.dromara.resource.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.resource.domain.BizPersonnelProject;

import java.io.Serial;
import java.util.Date;

/**
 * 人员项目经验业务对象
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BizPersonnelProject.class, reverseConvertGenerate = false)
public class BizPersonnelProjectBo extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "人员ID")
    private Long personnelId;

    @Schema(description = "项目名称")
    private String projectName;

    @Schema(description = "项目角色")
    private String projectRole;

    @Schema(description = "项目介绍")
    private String projectDescription;

    @Schema(description = "开始日期")
    private Date startDate;

    @Schema(description = "结束日期")
    private Date endDate;

    @Schema(description = "备注")
    private String remark;

}

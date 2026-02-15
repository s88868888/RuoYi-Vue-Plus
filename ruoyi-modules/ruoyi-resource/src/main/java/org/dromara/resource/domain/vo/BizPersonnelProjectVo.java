package org.dromara.resource.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.dromara.resource.domain.BizPersonnelProject;

import java.io.Serial;
import java.util.Date;

/**
 * 人员项目经验视图对象
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Data
@AutoMapper(target = BizPersonnelProject.class)
public class BizPersonnelProjectVo {

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

    @Schema(description = "创建时间")
    private Date createTime;

}

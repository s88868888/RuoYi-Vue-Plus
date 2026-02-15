package org.dromara.resource.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.dromara.resource.domain.BizPerformance;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 业绩案例视图对象 biz_performance
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Data
@AutoMapper(target = BizPerformance.class)
public class BizPerformanceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "关联部门ID")
    private Long deptId;

    @Schema(description = "项目名称")
    private String name;

    @Schema(description = "业绩分类")
    private String performanceCategory;

    @Schema(description = "项目所在省份")
    private String projectProvince;

    @Schema(description = "项目所在城市")
    private String projectCity;

    @Schema(description = "业主单位性质")
    private String ownerUnitNature;

    @Schema(description = "业主单位名称")
    private String ownerUnitName;

    @Schema(description = "业主单位联系人")
    private String ownerUnitContact;

    @Schema(description = "项目状态")
    private String projectStatus;

    @Schema(description = "签约日期")
    private Date signingDate;

    @Schema(description = "中标日期")
    private Date bidDate;

    @Schema(description = "开工日期")
    private Date startDate;

    @Schema(description = "竣工日期")
    private Date completionDate;

    @Schema(description = "合同金额")
    private BigDecimal contractAmount;

    @Schema(description = "中标金额")
    private BigDecimal bidAmount;

    @Schema(description = "中标单价")
    private BigDecimal bidUnitPrice;

    @Schema(description = "项目所在地")
    private String projectLocation;

    @Schema(description = "住建部门")
    private String constructionDept;

    @Schema(description = "任务单位")
    private String taskUnit;

    @Schema(description = "工程规模")
    private String projectScale;

    @Schema(description = "实施部门")
    private String implementationDept;

    @Schema(description = "工程内容")
    private String projectContent;

    @Schema(description = "工程分析")
    private String projectAnalysis;

    @Schema(description = "其他工程特性描述")
    private String otherFeatures;

    @Schema(description = "工艺类型")
    private String processType;

    @Schema(description = "项目负责人")
    private String projectManager;

    @Schema(description = "技术负责人")
    private String technicalManager;

    @Schema(description = "项目经理")
    private String projectDirector;

    @Schema(description = "中标通知附件")
    private String bidNoticeAttachment;

    @Schema(description = "合同附件")
    private String contractAttachment;

    @Schema(description = "合同图片")
    private String contractImages;

    @Schema(description = "验收资料附件")
    private String acceptanceAttachment;

    @Schema(description = "其他附件")
    private String otherAttachment;

    @Schema(description = "中标公示链接")
    private String bidPublicityLink;

    @Schema(description = "数据权限类型")
    private String dataPermissionType;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "创建人")
    private String createByName;

}

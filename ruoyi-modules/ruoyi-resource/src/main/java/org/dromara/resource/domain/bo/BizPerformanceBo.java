package org.dromara.resource.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.resource.domain.BizPerformance;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 业绩案例业务对象 biz_performance
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BizPerformance.class, reverseConvertGenerate = false)
public class BizPerformanceBo extends BaseEntity {

    @NotNull(message = "主键ID不能为空", groups = {EditGroup.class})
    private Long id;

    private Long deptId;

    @NotBlank(message = "项目名称不能为空", groups = {AddGroup.class, EditGroup.class})
    private String name;

    @NotBlank(message = "业绩分类不能为空", groups = {AddGroup.class, EditGroup.class})
    private String performanceCategory;

    @NotBlank(message = "项目所在省份不能为空", groups = {AddGroup.class, EditGroup.class})
    private String projectProvince;

    private String projectCity;

    @NotBlank(message = "业主单位性质不能为空", groups = {AddGroup.class, EditGroup.class})
    private String ownerUnitNature;

    @NotBlank(message = "业主单位名称不能为空", groups = {AddGroup.class, EditGroup.class})
    private String ownerUnitName;

    private String ownerUnitContact;
    private String projectStatus;
    private Date signingDate;
    private Date bidDate;
    private Date startDate;
    private Date completionDate;
    private BigDecimal contractAmount;
    private BigDecimal bidAmount;
    private BigDecimal bidUnitPrice;
    private String projectLocation;
    private String constructionDept;
    private String taskUnit;
    private String projectScale;
    private String implementationDept;
    private String projectContent;
    private String projectAnalysis;
    private String otherFeatures;
    private String processType;
    private String projectManager;
    private String technicalManager;
    private String projectDirector;
    private String bidNoticeAttachment;
    private String contractAttachment;
    private String contractImages;
    private String acceptanceAttachment;
    private String otherAttachment;
    private String bidPublicityLink;
    private String dataPermissionType;
    private String remark;

}

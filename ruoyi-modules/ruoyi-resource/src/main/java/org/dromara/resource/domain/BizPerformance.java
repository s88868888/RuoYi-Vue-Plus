package org.dromara.resource.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 业绩案例对象 biz_performance
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_performance")
public class BizPerformance extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 关联部门ID
     */
    private Long deptId;

    /**
     * 项目名称
     */
    private String name;

    /**
     * 业绩分类
     */
    private String performanceCategory;

    /**
     * 项目所在省份
     */
    private String projectProvince;

    /**
     * 项目所在城市
     */
    private String projectCity;

    /**
     * 业主单位性质
     */
    private String ownerUnitNature;

    /**
     * 业主单位名称
     */
    private String ownerUnitName;

    /**
     * 业主单位联系人
     */
    private String ownerUnitContact;

    /**
     * 项目状态
     */
    private String projectStatus;

    /**
     * 签约日期
     */
    private Date signingDate;

    /**
     * 中标日期
     */
    private Date bidDate;

    /**
     * 开工日期
     */
    private Date startDate;

    /**
     * 竣工日期
     */
    private Date completionDate;

    /**
     * 合同金额
     */
    private BigDecimal contractAmount;

    /**
     * 中标金额
     */
    private BigDecimal bidAmount;

    /**
     * 中标单价
     */
    private BigDecimal bidUnitPrice;

    /**
     * 项目所在地
     */
    private String projectLocation;

    /**
     * 住建部门
     */
    private String constructionDept;

    /**
     * 任务单位
     */
    private String taskUnit;

    /**
     * 工程规模
     */
    private String projectScale;

    /**
     * 实施部门
     */
    private String implementationDept;

    /**
     * 工程内容
     */
    private String projectContent;

    /**
     * 工程分析
     */
    private String projectAnalysis;

    /**
     * 其他工程特性描述
     */
    private String otherFeatures;

    /**
     * 工艺类型
     */
    private String processType;

    /**
     * 项目负责人
     */
    private String projectManager;

    /**
     * 技术负责人
     */
    private String technicalManager;

    /**
     * 项目经理
     */
    private String projectDirector;

    /**
     * 中标通知附件
     */
    private String bidNoticeAttachment;

    /**
     * 合同附件
     */
    private String contractAttachment;

    /**
     * 合同图片
     */
    private String contractImages;

    /**
     * 验收资料附件
     */
    private String acceptanceAttachment;

    /**
     * 其他附件
     */
    private String otherAttachment;

    /**
     * 中标公示链接
     */
    private String bidPublicityLink;

    /**
     * 数据权限类型（0公域 1私域）
     */
    private String dataPermissionType;

    /**
     * 备注
     */
    private String remark;

}

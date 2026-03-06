package org.dromara.resource.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.dromara.resource.domain.BizCompetitor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 竞争公司视图对象 biz_competitor
 *
 * @author ruoyi
 * @date 2026-03-06
 */
@Data
@AutoMapper(target = BizCompetitor.class)
public class BizCompetitorVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "关联部门ID")
    private Long deptId;

    @Schema(description = "竞争公司名称")
    private String companyName;

    @Schema(description = "公司类型")
    private String companyType;

    @Schema(description = "主营业务")
    private String businessScope;

    @Schema(description = "注册资本（万元）")
    private String registeredCapital;

    @Schema(description = "成立年份")
    private String foundedYear;

    @Schema(description = "所在省份")
    private String province;

    @Schema(description = "所在城市")
    private String city;

    @Schema(description = "官网地址")
    private String website;

    @Schema(description = "联系人")
    private String contactPerson;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "联系邮箱")
    private String contactEmail;

    @Schema(description = "竞争优势")
    private String strengths;

    @Schema(description = "竞争劣势")
    private String weaknesses;

    @Schema(description = "主要产品/服务")
    private String mainProducts;

    @Schema(description = "竞争级别")
    private String competitorLevel;

    @Schema(description = "附件URL")
    private String attachmentUrl;

    @Schema(description = "附件名称")
    private String attachmentName;

    @Schema(description = "数据权限类型")
    private String dataPermissionType;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "创建人")
    private String createByName;

}

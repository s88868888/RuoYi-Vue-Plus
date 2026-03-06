package org.dromara.resource.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 竞争公司对象 biz_competitor
 *
 * @author ruoyi
 * @date 2026-03-06
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_competitor")
public class BizCompetitor extends TenantEntity {

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
     * 竞争公司名称
     */
    private String companyName;

    /**
     * 公司类型
     */
    private String companyType;

    /**
     * 主营业务
     */
    private String businessScope;

    /**
     * 注册资本（万元）
     */
    private String registeredCapital;

    /**
     * 成立年份
     */
    private String foundedYear;

    /**
     * 所在省份
     */
    private String province;

    /**
     * 所在城市
     */
    private String city;

    /**
     * 官网地址
     */
    private String website;

    /**
     * 联系人
     */
    private String contactPerson;

    /**
     * 联系电话
     */
    private String contactPhone;

    /**
     * 联系邮箱
     */
    private String contactEmail;

    /**
     * 竞争优势
     */
    private String strengths;

    /**
     * 竞争劣势
     */
    private String weaknesses;

    /**
     * 主要产品/服务
     */
    private String mainProducts;

    /**
     * 竞争级别（强/中/弱）
     */
    private String competitorLevel;

    /**
     * 附件URL（逗号分隔）
     */
    private String attachmentUrl;

    /**
     * 附件名称（逗号分隔）
     */
    private String attachmentName;

    /**
     * 数据权限类型（0私域 1公域）
     */
    private String dataPermissionType;

    /**
     * 备注
     */
    private String remark;

}

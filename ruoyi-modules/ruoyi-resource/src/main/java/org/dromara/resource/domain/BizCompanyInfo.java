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
 * 企业信息对象 biz_company_info
 *
 * @author ruoyi
 * @date 2026-02-10
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_company_info")
public class BizCompanyInfo extends TenantEntity {

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

    // ==================== 营业执照信息 ====================

    /**
     * 统一社会信用代码
     */
    private String unifiedCreditCode;

    /**
     * 法定代表人
     */
    private String legalPerson;

    /**
     * 注册资本
     */
    private String registeredCapital;

    /**
     * 企业性质
     */
    private String enterpriseNature;

    /**
     * 成立日期
     */
    private Date establishmentDate;

    /**
     * 登记机关
     */
    private String registrationAuthority;

    /**
     * 核准日期
     */
    private Date registrationDate;

    /**
     * 营业期限是否长期（0否 1是）
     */
    private String businessLongTerm;

    /**
     * 营业期限截止日期
     */
    private Date businessEndDate;

    /**
     * 企业简称
     */
    private String enterpriseAbbr;

    /**
     * 企业所在地区
     */
    private String enterpriseRegion;

    /**
     * 注册地址
     */
    private String registeredAddress;

    /**
     * 经营范围
     */
    private String businessScope;

    /**
     * 营业执照图片
     */
    private String businessLicenseImg;

    // ==================== 企业信息 ====================

    /**
     * 企业LOGO
     */
    private String companyLogo;

    /**
     * 税务登记证
     */
    private String taxCertificate;

    /**
     * 社保附件
     */
    private String socialSecurityAttachment;

    /**
     * 公积金附件
     */
    private String housingFundAttachment;

    /**
     * 纳税人资格证
     */
    private String taxpayerCertificate;

    /**
     * 实验室附件
     */
    private String labAttachment;

    /**
     * 企业规模
     */
    private String enterpriseScale;

    /**
     * 行业类别
     */
    private String industryCategory;

    /**
     * 公司地址
     */
    private String companyAddress;

    /**
     * 公司网站
     */
    private String companyWebsite;

    /**
     * 企业电话
     */
    private String enterprisePhone;

    /**
     * 企业传真
     */
    private String enterpriseFax;

    /**
     * 研发费用
     */
    private BigDecimal rdExpense;

    /**
     * 员工总数
     */
    private Integer totalEmployees;

    /**
     * 高级职称人数
     */
    private Integer seniorTitleCount;

    /**
     * 中级职称人数
     */
    private Integer juniorTitleCount;

    /**
     * 投标经理人数
     */
    private Integer bidManagerCount;

    /**
     * 投标员人数
     */
    private Integer bidWorkerCount;

    /**
     * 造价师人数
     */
    private Integer bidCostManagerCount;

    /**
     * 证书编号
     */
    private String certificateNumber;

    /**
     * 组织架构图
     */
    private String orgStructure;

    /**
     * 安全生产许可证图片
     */
    private String safetyPermitImg;

    /**
     * 安全许可证到期日
     */
    private Date safetyPermitExpiry;

    // ==================== 开户信息 ====================

    /**
     * 开户名称
     */
    private String accountName;

    /**
     * 开户银行
     */
    private String accountBank;

    /**
     * 银行账号
     */
    private String accountNumber;

    /**
     * 联行号
     */
    private String bankUnionNumber;

    /**
     * 银行电话
     */
    private String bankPhone;

    /**
     * 银行地址
     */
    private String bankAddress;

    /**
     * 开户许可证图片
     */
    private String bankAccountImg;

    // ==================== 其他信息 ====================

    /**
     * 年生产能力
     */
    private String annualProductionCapacity;

    /**
     * 控股服务
     */
    private String holdingService;

    /**
     * 控股股东比例
     */
    private String holdingShareholderRatio;

    /**
     * 委托服务
     */
    private String entrustedService;

    /**
     * 实际股东比例
     */
    private String actualShareholderRatio;

    /**
     * 管理体系认证
     */
    private String managementCertification;

    /**
     * 社会团体等级
     */
    private String socialGroupLevel;

    /**
     * 信用处罚信息
     */
    private String creditPenaltyInfo;

    /**
     * 近期诉讼
     */
    private String recentLitigation;

}

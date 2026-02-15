package org.dromara.resource.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.resource.domain.BizCompanyInfo;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 企业信息视图对象 biz_company_info
 *
 * @author ruoyi
 * @date 2026-02-10
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ExcelIgnoreUnannotated
@AutoMapper(target = BizCompanyInfo.class)
public class BizCompanyInfoVo extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 关联部门ID
     */
    private Long deptId;

    // ==================== 营业执照信息 ====================

    private String unifiedCreditCode;
    private String legalPerson;
    private String registeredCapital;
    private String enterpriseNature;
    private Date establishmentDate;
    private String registrationAuthority;
    private Date registrationDate;
    private String businessLongTerm;
    private Date businessEndDate;
    private String enterpriseAbbr;
    private String enterpriseRegion;
    private String registeredAddress;
    private String businessScope;
    private String businessLicenseImg;

    // ==================== 企业信息 ====================

    private String companyLogo;
    private String taxCertificate;
    private String socialSecurityAttachment;
    private String housingFundAttachment;
    private String taxpayerCertificate;
    private String labAttachment;
    private String enterpriseScale;
    private String industryCategory;
    private String companyAddress;
    private String companyWebsite;
    private String enterprisePhone;
    private String enterpriseFax;
    private BigDecimal rdExpense;
    private Integer totalEmployees;
    private Integer seniorTitleCount;
    private Integer juniorTitleCount;
    private Integer bidManagerCount;
    private Integer bidWorkerCount;
    private Integer bidCostManagerCount;
    private String certificateNumber;
    private String orgStructure;
    private String safetyPermitImg;
    private Date safetyPermitExpiry;

    // ==================== 开户信息 ====================

    private String accountName;
    private String accountBank;
    private String accountNumber;
    private String bankUnionNumber;
    private String bankPhone;
    private String bankAddress;
    private String bankAccountImg;

    // ==================== 其他信息 ====================

    private String annualProductionCapacity;
    private String holdingService;
    private String holdingShareholderRatio;
    private String entrustedService;
    private String actualShareholderRatio;
    private String managementCertification;
    private String socialGroupLevel;
    private String creditPenaltyInfo;
    private String recentLitigation;

}

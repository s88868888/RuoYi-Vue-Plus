package org.dromara.resource.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.resource.domain.BizCompanyInfo;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 企业信息业务对象 biz_company_info
 *
 * @author ruoyi
 * @date 2026-02-10
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BizCompanyInfo.class, reverseConvertGenerate = false)
public class BizCompanyInfoBo extends BaseEntity {

    /**
     * 主键ID
     */
    @NotNull(message = "主键ID不能为空", groups = {EditGroup.class})
    private Long id;

    /**
     * 关联部门ID
     */
    @NotNull(message = "部门ID不能为空", groups = {AddGroup.class, EditGroup.class})
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

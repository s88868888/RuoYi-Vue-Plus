package org.dromara.resource.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 公司列表卡片视图对象（合并 sys_dept + biz_company_info）
 *
 * @author ruoyi
 * @date 2026-02-10
 */
@Data
public class CompanyListVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 部门ID
     */
    private Long deptId;

    /**
     * 公司名称（来自 sys_dept.dept_name）
     */
    private String deptName;

    /**
     * 负责人ID（来自 sys_dept.leader）
     */
    private Long leader;

    /**
     * 负责人名称（翻译字段）
     */
    private String leaderName;

    /**
     * 联系电话（来自 sys_dept.phone）
     */
    private String phone;

    /**
     * 状态（来自 sys_dept.status）
     */
    private String status;

    /**
     * 统一社会信用代码（来自 biz_company_info）
     */
    private String unifiedCreditCode;

    /**
     * 企业LOGO（来自 biz_company_info）
     */
    private String companyLogo;

    /**
     * 企业简称（来自 biz_company_info）
     */
    private String enterpriseAbbr;

    /**
     * 企业信息ID（来自 biz_company_info.id）
     */
    private Long companyInfoId;

    // ==================== 统计字段（预留） ====================

    /**
     * 资质数量
     */
    private Integer qualificationCount;

    /**
     * 人员数量
     */
    private Integer personnelCount;

    /**
     * 证书数量
     */
    private Integer certificateCount;

}

package org.dromara.common.ai.domain.bo;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 公司信息业务对象
 *
 * @author ruoyi
 */
@Data
public class CompanyInfoBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 公司名称
     */
    @NotBlank(message = "公司名称不能为空")
    private String companyName;

    /**
     * 统一社会信用代码
     */
    private String creditCode;

    /**
     * 法定代表人
     */
    private String legalPerson;

    /**
     * 注册资本
     */
    private String registeredCapital;

    /**
     * 成立日期
     */
    private String establishDate;

    /**
     * 经营范围
     */
    private String businessScope;

    /**
     * 注册地址
     */
    private String registeredAddress;

    /**
     * 公司简介
     */
    private String companyProfile;

}

package org.dromara.common.ai.domain.bo;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 资质信息业务对象
 *
 * @author ruoyi
 */
@Data
public class QualificationInfoBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 资质类型
     */
    @NotBlank(message = "资质类型不能为空")
    private String qualificationType;

    /**
     * 资质名称
     */
    @NotBlank(message = "资质名称不能为空")
    private String qualificationName;

    /**
     * 证书编号
     */
    private String certificateNo;

    /**
     * 发证机关
     */
    private String issuingAuthority;

    /**
     * 发证日期
     */
    private String issueDate;

    /**
     * 有效期至
     */
    private String validUntil;

    /**
     * 资质等级
     */
    private String qualificationLevel;

    /**
     * 备注
     */
    private String remark;

}

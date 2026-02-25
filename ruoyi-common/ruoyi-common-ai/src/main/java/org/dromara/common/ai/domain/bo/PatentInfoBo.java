package org.dromara.common.ai.domain.bo;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 专利信息业务对象
 *
 * @author ruoyi
 */
@Data
public class PatentInfoBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 专利名称
     */
    @NotBlank(message = "专利名称不能为空")
    private String patentName;

    /**
     * 专利类型
     */
    private String patentType;

    /**
     * 专利号
     */
    private String patentNo;

    /**
     * 申请日期
     */
    private String applicationDate;

    /**
     * 授权日期
     */
    private String authorizationDate;

    /**
     * 发明人
     */
    private String inventor;

    /**
     * 专利摘要
     */
    private String patentAbstract;

    /**
     * 专利状态
     */
    private String patentStatus;

}

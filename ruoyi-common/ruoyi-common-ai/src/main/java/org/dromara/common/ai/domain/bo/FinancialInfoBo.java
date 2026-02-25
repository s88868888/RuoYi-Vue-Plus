package org.dromara.common.ai.domain.bo;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 财务信息业务对象
 *
 * @author ruoyi
 */
@Data
public class FinancialInfoBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 年度
     */
    @NotBlank(message = "年度不能为空")
    private String year;

    /**
     * 营业收入
     */
    private String revenue;

    /**
     * 净利润
     */
    private String profit;

    /**
     * 总资产
     */
    private String assets;

    /**
     * 负债总额
     */
    private String liabilities;

    /**
     * 净资产
     */
    private String netAssets;

    /**
     * 资产负债率
     */
    private String assetLiabilityRatio;

    /**
     * 备注
     */
    private String remark;

}

package org.dromara.common.ai.domain.bo;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 产品信息业务对象
 *
 * @author ruoyi
 */
@Data
public class ProductInfoBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 产品名称
     */
    @NotBlank(message = "产品名称不能为空")
    private String productName;

    /**
     * 产品类别
     */
    private String category;

    /**
     * 产品型号
     */
    private String model;

    /**
     * 产品描述
     */
    private String description;

    /**
     * 产品特点
     */
    private String features;

    /**
     * 技术参数
     */
    private String technicalParams;

    /**
     * 应用场景
     */
    private String applicationScenario;

}

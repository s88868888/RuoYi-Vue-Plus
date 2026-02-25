package org.dromara.common.ai.domain.bo;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 业绩案例业务对象
 *
 * @author ruoyi
 */
@Data
public class PerformanceCaseBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 项目名称
     */
    @NotBlank(message = "项目名称不能为空")
    private String projectName;

    /**
     * 客户名称
     */
    private String client;

    /**
     * 项目金额
     */
    private String projectAmount;

    /**
     * 项目类型
     */
    private String projectType;

    /**
     * 开始日期
     */
    private String startDate;

    /**
     * 完成日期
     */
    private String completionDate;

    /**
     * 项目描述
     */
    private String description;

    /**
     * 项目成果
     */
    private String achievement;

}

package org.dromara.review.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * AI提示词模板对象 review_prompt_template
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("review_prompt_template")
public class ReviewPromptTemplate extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 模板名称
     */
    private String name;

    /**
     * 模板类型
     */
    private String type;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 用户提示词
     */
    private String userPrompt;

    /**
     * 输出格式
     */
    private String outputFormat;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 温度参数
     */
    private BigDecimal temperature;

    /**
     * 状态
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

}

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
     * 模型名称（legacy，新版用 modelConfigId 取代；保留做兜底回退）
     */
    private String modelName;

    /**
     * AI模型配置ID（关联 review_model_config，purpose=chat）
     */
    private Long modelConfigId;

    /**
     * OCR模型配置ID（关联 review_model_config，purpose=ocr）
     * 扫描件 OCR 用此配置；为空时回退到全局最早 enabled 的 OCR 配置
     */
    private Long ocrConfigId;

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

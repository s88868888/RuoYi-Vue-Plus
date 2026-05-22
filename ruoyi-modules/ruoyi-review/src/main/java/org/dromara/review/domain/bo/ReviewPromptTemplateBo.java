package org.dromara.review.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.review.domain.ReviewPromptTemplate;

import java.math.BigDecimal;

/**
 * AI提示词模板业务对象 review_prompt_template
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ReviewPromptTemplate.class, reverseConvertGenerate = false)
public class ReviewPromptTemplateBo extends BaseEntity {

    /**
     * 主键ID
     */
    @NotNull(message = "主键不能为空", groups = {EditGroup.class})
    private Long id;

    /**
     * 模板名称
     */
    @NotBlank(message = "模板名称不能为空", groups = {AddGroup.class, EditGroup.class})
    private String name;

    /**
     * 模板类型
     */
    @NotBlank(message = "模板类型不能为空", groups = {AddGroup.class, EditGroup.class})
    private String type;

    /**
     * 系统提示词
     */
    @NotBlank(message = "系统提示词不能为空", groups = {AddGroup.class, EditGroup.class})
    private String systemPrompt;

    /**
     * 用户提示词
     */
    @NotBlank(message = "用户提示词不能为空", groups = {AddGroup.class, EditGroup.class})
    private String userPrompt;

    /**
     * 输出格式
     */
    private String outputFormat;

    /**
     * 模型名称（legacy）
     */
    private String modelName;

    /**
     * AI模型配置ID（关联 review_model_config，purpose=chat）
     */
    private Long modelConfigId;

    /**
     * OCR模型配置ID（关联 review_model_config，purpose=ocr）
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

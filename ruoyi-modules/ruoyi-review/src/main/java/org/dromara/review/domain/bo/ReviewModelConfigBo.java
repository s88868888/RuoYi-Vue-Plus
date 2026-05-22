package org.dromara.review.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.review.domain.ReviewModelConfig;

import java.math.BigDecimal;

/**
 * AI 模型配置 业务对象 review_model_config
 *
 * @author Linson
 * @date 2026-05-22
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ReviewModelConfig.class, reverseConvertGenerate = false)
public class ReviewModelConfigBo extends BaseEntity {

    @NotNull(message = "主键不能为空", groups = {EditGroup.class})
    private Long id;

    @NotBlank(message = "名称不能为空", groups = {AddGroup.class, EditGroup.class})
    private String name;

    @NotBlank(message = "编码不能为空", groups = {AddGroup.class, EditGroup.class})
    private String code;

    @NotBlank(message = "提供方不能为空", groups = {AddGroup.class, EditGroup.class})
    private String provider;

    @NotBlank(message = "模型ID不能为空", groups = {AddGroup.class, EditGroup.class})
    private String modelName;

    private String baseUrl;
    private String apiKey;
    private Integer numCtx;
    private Integer numPredict;
    private Integer maxTokens;
    private BigDecimal temperature;
    private BigDecimal topP;
    private Long timeoutMs;
    private String kvCacheType;
    private String extraOptions;
    private String enabled;
    private String purpose;
    private String remark;
}

package org.dromara.review.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.review.domain.ReviewModelConfig;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * AI 模型配置 视图对象 review_model_config
 *
 * @author Linson
 * @date 2026-05-22
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ReviewModelConfig.class)
public class ReviewModelConfigVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String code;
    private String provider;
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
    private Date createTime;
    private Date updateTime;
}

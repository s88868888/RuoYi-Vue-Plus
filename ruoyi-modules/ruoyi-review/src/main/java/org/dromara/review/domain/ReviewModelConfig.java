package org.dromara.review.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * AI 模型配置对象 review_model_config
 * <p>
 * 抽出 yml 中的模型参数，让运营在线编辑、热切换；
 * review_prompt_template.model_config_id 关联到此表。
 *
 * @author Linson
 * @date 2026-05-22
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("review_model_config")
public class ReviewModelConfig extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /** 显示名称 */
    private String name;

    /** 唯一编码 */
    private String code;

    /** 提供方：ollama / dashscope / qwen-vl */
    private String provider;

    /** 模型 ID */
    private String modelName;

    /** 服务地址 */
    private String baseUrl;

    /** API 密钥 */
    private String apiKey;

    /** 上下文窗口（ollama） */
    private Integer numCtx;

    /** 单次输出 token 上限（ollama） */
    private Integer numPredict;

    /** 单次输出 token 上限（dashscope） */
    private Integer maxTokens;

    /** 温度 */
    private BigDecimal temperature;

    /** top_p */
    private BigDecimal topP;

    /** 超时（毫秒） */
    private Long timeoutMs;

    /** KV 缓存量化 */
    private String kvCacheType;

    /** 额外选项（JSON） */
    private String extraOptions;

    /** 启用：0=禁用 1=启用 */
    private String enabled;

    /** 用途：chat / ocr */
    private String purpose;

    /** 备注 */
    private String remark;
}

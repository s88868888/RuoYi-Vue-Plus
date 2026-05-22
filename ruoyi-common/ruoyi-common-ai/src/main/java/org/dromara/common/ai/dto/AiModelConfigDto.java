package org.dromara.common.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * AI 模型配置 DTO（跨模块传递）
 * <p>
 * 由 ruoyi-review 的 ReviewModelConfig 转出，喂给 AiChatService.chatWithConfig 使用。
 * 字段集合按 ollama / dashscope 两种 provider 通用化设计：
 * - ollama 用 numCtx/numPredict/topP，apiKey 留空
 * - dashscope 用 maxTokens，apiKey 必填
 * <p>
 * 重写 equals/hashCode（Lombok @Data 默认包含全字段）以便 AiChatService 用作缓存失效判定。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModelConfigDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 配置 ID（由调用方从 DB 取出，作为 AiChatService 缓存 key） */
    private Long id;

    /** 唯一编码（程序引用） */
    private String code;

    /** 提供方：ollama / dashscope / paddleocr / qwen-vl-ocr */
    private String provider;

    /** 模型 ID（如 qwen-long-latest / qwen3.6:35b-a3b-q4_K_M / paddlex-ocr） */
    private String modelName;

    /** 服务地址（ollama必填；dashscope可空走 starter 默认） */
    private String baseUrl;

    /** API 密钥；支持 ${ENV_VAR} 占位符（运行时由 AiChatService 解析） */
    private String apiKey;

    /** 上下文窗口（ollama 专用） */
    private Integer numCtx;

    /** 单次输出 token 上限（ollama 专用） */
    private Integer numPredict;

    /** 单次输出 token 上限（dashscope 专用） */
    private Integer maxTokens;

    /** 温度参数 */
    private BigDecimal temperature;

    /** top_p 采样 */
    private BigDecimal topP;

    /** HTTP 调用超时（毫秒） */
    private Long timeoutMs;

    /** KV 缓存量化（ollama，f16/q8_0/q4_0；表里目前仅记录，需配 server 环境变量生效） */
    private String kvCacheType;

    /** 用途：chat / ocr */
    private String purpose;

    /** 额外选项（已解析的 JSON）：format/think 等运行时参数 */
    private Map<String, Object> extraOptions;
}

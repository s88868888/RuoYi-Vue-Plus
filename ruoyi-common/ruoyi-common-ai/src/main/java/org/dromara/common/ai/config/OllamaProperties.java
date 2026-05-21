package org.dromara.common.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 本地 Ollama 配置
 * <p>
 * 控制是否启用本地大模型审核通道。启用后 ReviewAgent 会根据 prompt 模板的 model_name
 * 字段（前缀 ollama:）路由到本地模型，否则维持原 DashScope 链路。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "review.ai.local")
public class OllamaProperties {

    /** 是否启用本地 Ollama 通道 */
    private boolean enabled = false;

    /** Ollama 服务地址 */
    private String baseUrl = "http://192.168.169.205:11434";

    /** 默认模型（模板 model_name 留空时使用） */
    private String model = "qwen3.6:35b-a3b-q4_K_M";

    /** 上下文窗口（token 数），4090 24GB 推荐 32768，加大易 OOM；prompt+output 都吃这里 */
    private int numCtx = 32768;

    /** 单次推理输出 token 上限（防退化重复无限生成）。-1 表示不限，建议 6000~8000 */
    private int numPredict = 8000;

    /** 温度参数 */
    private double temperature = 0.2;

    /** top_p */
    private double topP = 0.8;

    /** 调用超时时间（毫秒），35B 模型生成 18 页 JSON 约 30~60 秒，预留 5 分钟 */
    private long timeoutMs = 300000L;
}

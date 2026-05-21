package org.dromara.common.ai.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 本地 Ollama 客户端配置
 * <p>
 * 仅当 review.ai.local.enabled=true 时才注册 Bean，避免空载情况下被加载。
 * 不使用 spring-ai-starter-model-ollama，手动构造 OllamaChatModel，避免与
 * spring-ai-alibaba-starter-dashscope 的 auto-config 冲突（两者都会去抢
 * 默认 ChatClient.Builder 注入）。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "review.ai.local", name = "enabled", havingValue = "true")
public class OllamaChatModelConfig {

    private final OllamaProperties properties;

    @Bean(name = "ollamaChatModel", defaultCandidate = false)
    public OllamaChatModel ollamaChatModel() {
        log.info("[OllamaChatModelConfig] 启用本地 Ollama 通道: baseUrl={}, model={}, numCtx={}, timeout={}ms",
            properties.getBaseUrl(), properties.getModel(), properties.getNumCtx(), properties.getTimeoutMs());

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(30));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));

        RestClient.Builder restClientBuilder = RestClient.builder()
            .requestFactory(requestFactory);

        OllamaApi ollamaApi = OllamaApi.builder()
            .baseUrl(properties.getBaseUrl())
            .restClientBuilder(restClientBuilder)
            .build();

        OllamaChatOptions defaultOptions = OllamaChatOptions.builder()
            .model(properties.getModel())
            .temperature(properties.getTemperature())
            .topP(properties.getTopP())
            .numCtx(properties.getNumCtx())
            .build();

        return OllamaChatModel.builder()
            .ollamaApi(ollamaApi)
            .defaultOptions(defaultOptions)
            .modelManagementOptions(ModelManagementOptions.defaults())
            .build();
    }
}

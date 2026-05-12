package org.dromara.common.ai.service;

import com.alibaba.cloud.ai.advisor.DashScopeDocumentAnalysisAdvisor;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import org.springframework.util.MimeTypeUtils;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 通用 AI 调用服务
 *
 * 提供统一的 AI 调用入口，封装 Spring AI 的具体实现
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Slf4j
@Service
public class AiChatService {

    private final EmbeddingModel embeddingModel;
    private final ChatClient chatClient;
    private final ChatClient multiModalChatClient;
    private final DashScopeDocumentAnalysisAdvisor documentAdvisor;

    /**
     * 最大重试次数
     */
    @Value("${ai.retry.max-attempts:5}")
    private int maxRetryAttempts;

    /**
     * 初始退避时间（毫秒）
     */
    @Value("${ai.retry.initial-backoff-ms:2000}")
    private long initialBackoffMs;

    /**
     * 退避倍数
     */
    @Value("${ai.retry.backoff-multiplier:2.0}")
    private double backoffMultiplier;

    /**
     * 最大退避时间（毫秒）
     */
    @Value("${ai.retry.max-backoff-ms:60000}")
    private long maxBackoffMs;

    public AiChatService(EmbeddingModel embeddingModel,
                         ChatClient.Builder chatClientBuilder,
                         @Value("${spring.ai.dashscope.api-key}") String apiKey,
                         @Value("${spring.ai.dashscope.base-url:}") String baseUrl,
                         @Value("${spring.ai.dashscope.multimodal-completions-path:/api/v1/services/aigc/multimodal-generation/generation}") String multiModalCompletionsPath,
                         @Value("${spring.ai.dashscope.http-client.read-timeout:300000}") int aiReadTimeout) {
        this.embeddingModel = embeddingModel;
        this.documentAdvisor = new DashScopeDocumentAnalysisAdvisor(new SimpleApiKey(apiKey));
        // 不注册为 defaultAdvisors，避免普通 chat 请求触发文档解析导致 URL 错误
        this.chatClient = chatClientBuilder.build();

        // 构建带超时配置的 RestClient，避免 multimodal 请求被 Jetty 默认超时中断
        // 需要同时设置 total timeout (setReadTimeout) 和 idle timeout (HttpClient.setIdleTimeout)
        org.eclipse.jetty.client.HttpClient jettyHttpClient = new org.eclipse.jetty.client.HttpClient();
        jettyHttpClient.setIdleTimeout(aiReadTimeout);  // 空闲超时（默认30s）
        jettyHttpClient.setConnectTimeout(30000);       // 连接超时
        try {
            jettyHttpClient.start();
        } catch (Exception e) {
            throw new RuntimeException("启动 Jetty HttpClient 失败", e);
        }
        org.springframework.http.client.JettyClientHttpRequestFactory jettyFactory =
            new org.springframework.http.client.JettyClientHttpRequestFactory(jettyHttpClient);
        jettyFactory.setReadTimeout(java.time.Duration.ofMillis(aiReadTimeout));
        org.springframework.web.client.RestClient.Builder multiModalRestClientBuilder =
            org.springframework.web.client.RestClient.builder().requestFactory(jettyFactory);

        DashScopeApi.Builder dashScopeApiBuilder = DashScopeApi.builder()
            .apiKey(apiKey)
            .completionsPath(multiModalCompletionsPath)
            .restClientBuilder(multiModalRestClientBuilder);
        if (baseUrl != null && !baseUrl.isBlank()) {
            dashScopeApiBuilder.baseUrl(baseUrl);
        }
        DashScopeChatModel multiModalChatModel = DashScopeChatModel.builder()
            .dashScopeApi(dashScopeApiBuilder.build())
            .defaultOptions(GENERATE_OPTIONS)
            .build();
        this.multiModalChatClient = ChatClient.builder(multiModalChatModel).build();
    }

    private static final DashScopeChatOptions CHAT_OPTIONS = DashScopeChatOptions.builder()
        .withModel("qwen-plus")
        .withIncrementalOutput(true)
        .withTemperature(0.3)
        .withTopP(0.9)
        .build();

    private static final DashScopeChatOptions GENERATE_OPTIONS = DashScopeChatOptions.builder()
        .withModel("qwen3.6-plus")
        .withMultiModel(true)
        .withStream(true)
        .withIncrementalOutput(true)
        .withTemperature(0.3)
        .withTopP(0.9)
        .build();

    private static final DashScopeChatOptions VISION_OPTIONS = DashScopeChatOptions.builder()
        .withModel("qwen3-vl-plus")
        .withMultiModel(true)
        .withTemperature(0.1)
        .withTopP(0.8)
        .withMaxToken(4096)
        .build();

    private static final DashScopeChatOptions DOC_OPTIONS = DashScopeChatOptions.builder()
        .withModel("qwen-long-latest")
        .withTemperature(0.3)
        .withMaxToken(8192)
        .withTopP(0.8)
        .build();

    private static final int CHAT_MESSAGE_MAX_LENGTH = 12000;
    private static final int GENERATE_PROMPT_MAX_LENGTH = 24000;

    /**
     * 简单的文本对话（qwen-plus）
     *
     * @param message 用户消息
     * @return AI 回复
     */
    public String chat(String message) {
        return callWithRetry(() -> chatClient.prompt()
            .user(message)
            .options(CHAT_OPTIONS)
            .call()
            .content());
    }

    /**
     * 带系统提示词的对话（qwen-plus）
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @return AI 回复
     */
    public String chat(String systemPrompt, String userMessage) {
        String cleanMessage = sanitizeDashScopeText(userMessage, CHAT_MESSAGE_MAX_LENGTH);
        log.info("[chat] systemPrompt={}", systemPrompt);
        log.info("[chat] cleanMessage length={}", cleanMessage.length());
        final String finalCleanMessage = cleanMessage;
        return callWithRetry(() -> chatClient.prompt()
            .system(systemPrompt)
            .user(finalCleanMessage)
            .options(CHAT_OPTIONS)
            .call()
            .content());
    }

    /**
     * 带变量替换的系统提示词对话（qwen-plus）
     *
     * @param systemPromptTemplate 带变量的系统提示词模板，如：你好{name}，今天是{day}
     * @param variables            变量映射，如：{"name": "张三", "day": "星期一"}
     * @param userMessage          用户消息
     * @return AI 回复
     */
    public String chatWithTemplate(String systemPromptTemplate, Map<String, Object> variables, String userMessage) {
        String resolvedSystemPrompt = systemPromptTemplate;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            resolvedSystemPrompt = resolvedSystemPrompt.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        final String finalSystemPrompt = resolvedSystemPrompt;
        return callWithRetry(() -> chatClient.prompt()
            .system(finalSystemPrompt)
            .user(userMessage)
            .options(CHAT_OPTIONS)
            .call()
            .content());
    }

    /**
     * 使用 Prompt 对象进行对话（qwen-plus）
     *
     * @param prompt Spring AI Prompt 对象
     * @return AI 回复
     */
    public String chat(Prompt prompt) {
        return callWithRetry(() -> chatClient.prompt(prompt)
            .options(CHAT_OPTIONS)
            .call()
            .content());
    }

    /**
     * 标书章节内容生成（qwen3.5-plus）
     *
     * @param prompt 提示词
     * @return AI 回复
     */
    public String chatGenerate(String prompt) {
        String cleanPrompt = sanitizeDashScopeText(prompt, GENERATE_PROMPT_MAX_LENGTH);
        try {
            return multiModalChatClient.prompt()
                .user(cleanPrompt)
                .options(GENERATE_OPTIONS)
                .stream()
                .content()
                .collectList()
                .map(parts -> String.join("", parts))
                .block();
        } catch (Exception e) {
            if (isDashScopeUrlError(e) || isDashScopeIncrementalOutputError(e)) {
                log.warn("qwen3.5-plus 调用参数不兼容（URL/增量输出），自动降级为 qwen-plus 文本生成: {}", e.getMessage());
                return callWithRetry(() -> chatClient.prompt()
                    .user(cleanPrompt)
                    .options(CHAT_OPTIONS)
                    .call()
                    .content());
            }
            throw e;
        }
    }

    /**
     * 生成文本Embedding向量
     *
     * @param text 待向量化的文本
     * @return 向量数组
     */
    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }

    /**
     * 批量生成文本Embedding向量
     *
     * @param texts 待向量化的文本列表
     * @return 向量数组列表
     */
    public List<float[]> embed(List<String> texts) {
        List<float[]> results = new ArrayList<>();
        for (String text : texts) {
            results.add(embeddingModel.embed(text));
        }
        return results;
    }

    /**
     * 图片理解分析（qwen3-vl-plus）
     *
     * @param imageUrl 图片URL
     * @param prompt   分析提示词
     * @return AI 回复
     */
    public String chatWithImage(String imageUrl, String prompt) {
        try {
            return callWithRetry(() -> {
                try {
                    return multiModalChatClient.prompt()
                        .user(u -> {
                            try {
                                u.text(prompt).media(MimeTypeUtils.IMAGE_PNG, URI.create(imageUrl).toURL());
                            } catch (MalformedURLException e) {
                                throw new RuntimeException("图片URL格式错误: " + imageUrl, e);
                            }
                        })
                        .options(VISION_OPTIONS)
                        .call()
                        .content();
                } catch (Exception e) {
                    throw e;
                }
            });
        } catch (Exception e) {
            log.error("[chatWithImage] 图片分析失败, imageUrl={}", imageUrl, e);
            throw e;
        }
    }

    /**
     * 带系统提示词的图片理解分析（qwen3-vl-plus）
     *
     * @param systemPrompt 系统提示词
     * @param imageUrl     图片URL
     * @param userPrompt   用户提示词
     * @return AI 回复
     */
    public String chatWithImage(String systemPrompt, String imageUrl, String userPrompt) {
        try {
            return callWithRetry(() -> {
                try {
                    return multiModalChatClient.prompt()
                        .system(systemPrompt)
                        .user(u -> {
                            try {
                                u.text(userPrompt).media(MimeTypeUtils.IMAGE_PNG, URI.create(imageUrl).toURL());
                            } catch (MalformedURLException e) {
                                throw new RuntimeException("图片URL格式错误: " + imageUrl, e);
                            }
                        })
                        .options(VISION_OPTIONS)
                        .call()
                        .content();
                } catch (Exception e) {
                    throw e;
                }
            });
        } catch (Exception e) {
            log.error("[chatWithImage] 带系统提示词的图片分析失败, imageUrl={}", imageUrl, e);
            throw e;
        }
    }

    /**
     * 带系统提示词的图片理解分析 - 通过 Resource 传递图片（支持内网文件）
     *
     * @param systemPrompt  系统提示词
     * @param imageResource 图片资源（如 ByteArrayResource）
     * @param userPrompt    用户提示词
     * @return AI 回复
     */
    public String chatWithImage(String systemPrompt, Resource imageResource, String userPrompt) {
        try {
            return callWithRetry(() -> multiModalChatClient.prompt()
                .system(systemPrompt)
                .user(u -> u.text(userPrompt).media(MimeTypeUtils.IMAGE_PNG, imageResource))
                .options(VISION_OPTIONS)
                .call()
                .content());
        } catch (Exception e) {
            log.error("[chatWithImage] 通过Resource的图片分析失败", e);
            throw e;
        }
    }

    /**
     * 通过 Resource 对象传递文件进行分析
     *
     * @param resource    文件资源
     * @param userMessage 用户消息
     * @return AI 回复
     */
    public String chatWithDocument(Resource resource, String userMessage) {
        return callWithRetry(() -> chatClient.prompt()
            .advisors(documentAdvisor)
            .advisors(a -> a.param(DashScopeDocumentAnalysisAdvisor.RESOURCE, resource))
            .user(userMessage)
            .options(DOC_OPTIONS)
            .call()
            .content());
    }

    /**
     * 带系统提示词的文档分析（qwen-long-latest）
     * <p>
     * 将 system/user 指令分离，规则抽取等任务更稳定。
     */
    public String chatWithDocument(String systemPrompt, Resource resource, String userMessage) {
        return callWithRetry(() -> chatClient.prompt()
            .advisors(documentAdvisor)
            .advisors(a -> a.param(DashScopeDocumentAnalysisAdvisor.RESOURCE, resource))
            .system(systemPrompt)
            .user(userMessage)
            .options(DOC_OPTIONS)
            .call()
            .content());
    }

    /**
     * 通过 URL 传递文件进行分析
     *
     * @param url         文件 URL
     * @param userMessage 用户消息
     * @return AI 回复
     */
    public String chatWithDocumentUrl(String url, String userMessage) {
        return chatWithDocument(UrlResource.from(url), userMessage);
    }

    /**
     * 带指数退避的重试包装器
     * <p>
     * 针对 HTTP 429（限流/配额超限）等异常自动重试，使用指数退避策略避免打满API限额。
     * 其他非限流异常直接抛出，不进行重试。
     *
     * @param action 实际的 AI 调用逻辑
     * @return AI 回复
     */
    private String callWithRetry(Supplier<String> action) {
        long backoffMs = initialBackoffMs;

        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                return action.get();
            } catch (Exception e) {
                // 判断是否为限流/配额错误（429 或 Throttling）
                if (!isRateLimitError(e)) {
                    throw e;
                }

                if (attempt >= maxRetryAttempts) {
                    log.error("AI调用在{}次重试后仍然失败，放弃重试", maxRetryAttempts, e);
                    throw e;
                }

                log.warn("AI调用遇到限流(429)，第{}次重试，等待{}ms后重试。错误: {}",
                    attempt, backoffMs, e.getMessage());

                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试等待被中断", ie);
                }

                // 指数退避，不超过最大退避时间
                backoffMs = Math.min((long) (backoffMs * backoffMultiplier), maxBackoffMs);
            }
        }

        // 不应该到达这里
        throw new RuntimeException("AI调用重试逻辑异常");
    }

    /**
     * 判断异常是否为限流/配额错误
     */
    private boolean isRateLimitError(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("429")
            || message.contains("Throttling")
            || message.contains("quota")
            || message.contains("rate limit")
            || message.contains("Rate limit")
            || message.contains("AllocationQuota");
    }

    private String sanitizeDashScopeText(String input, int maxLength) {
        String cleanText = input == null ? "" : input
            .replaceAll("(?i)(https?|ftp|oss|file)://\\S+", "[链接已省略]")
            .replaceAll("(?i)\\b[\\w\\-]+\\.tmp\\b", "[文件已省略]");
        if (maxLength > 0 && cleanText.length() > maxLength) {
            cleanText = cleanText.substring(0, maxLength) + "\n...(内容已截断)";
        }
        return cleanText;
    }

    private boolean isDashScopeUrlError(Exception exception) {
        String allMessage = collectExceptionMessages(exception).toLowerCase(Locale.ROOT);
        return allMessage.contains("invalidparameter")
            && (allMessage.contains("url error") || allMessage.contains("error-url"));
    }

    private boolean isDashScopeIncrementalOutputError(Exception exception) {
        String allMessage = collectExceptionMessages(exception).toLowerCase(Locale.ROOT);
        return allMessage.contains("invalidparameter")
            && allMessage.contains("incremental_output");
    }

    private String collectExceptionMessages(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                if (!sb.isEmpty()) {
                    sb.append(" | ");
                }
                sb.append(current.getMessage());
            }
            current = current.getCause();
        }
        return sb.toString();
    }

}

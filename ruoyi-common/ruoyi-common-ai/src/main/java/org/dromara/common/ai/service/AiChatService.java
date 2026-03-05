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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    public AiChatService(EmbeddingModel embeddingModel,
                         ChatClient.Builder chatClientBuilder,
                         @Value("${spring.ai.dashscope.api-key}") String apiKey,
                         @Value("${spring.ai.dashscope.base-url:}") String baseUrl,
                         @Value("${spring.ai.dashscope.multimodal-completions-path:/api/v1/services/aigc/multimodal-generation/generation}") String multiModalCompletionsPath) {
        this.embeddingModel = embeddingModel;
        this.documentAdvisor = new DashScopeDocumentAnalysisAdvisor(new SimpleApiKey(apiKey));
        // 不注册为 defaultAdvisors，避免普通 chat 请求触发文档解析导致 URL 错误
        this.chatClient = chatClientBuilder.build();

        DashScopeApi.Builder dashScopeApiBuilder = DashScopeApi.builder()
            .apiKey(apiKey)
            .completionsPath(multiModalCompletionsPath);
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
        .withModel("qwen3.5-plus")
        .withMultiModel(true)
        .withStream(true)
        .withIncrementalOutput(true)
        .withTemperature(0.3)
        .withTopP(0.9)
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
        return chatClient.prompt()
            .user(message)
            .options(CHAT_OPTIONS)
            .call()
            .content();
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
        return chatClient.prompt()
            .system(systemPrompt)
            .user(cleanMessage)
            .options(CHAT_OPTIONS)
            .call()
            .content();
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
        return chatClient.prompt()
            .system(resolvedSystemPrompt)
            .user(userMessage)
            .options(CHAT_OPTIONS)
            .call()
            .content();
    }

    /**
     * 使用 Prompt 对象进行对话（qwen-plus）
     *
     * @param prompt Spring AI Prompt 对象
     * @return AI 回复
     */
    public String chat(Prompt prompt) {
        return chatClient.prompt(prompt)
            .options(CHAT_OPTIONS)
            .call()
            .content();
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
                return chatClient.prompt()
                    .user(cleanPrompt)
                    .options(CHAT_OPTIONS)
                    .call()
                    .content();
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
     * 通过 Resource 对象传递文件进行分析
     *
     * @param resource    文件资源
     * @param userMessage 用户消息
     * @return AI 回复
     */
    public String chatWithDocument(Resource resource, String userMessage) {
        return chatClient.prompt()
            .advisors(documentAdvisor)
            .advisors(a -> a.param(DashScopeDocumentAnalysisAdvisor.RESOURCE, resource))
            .user(userMessage)
            .options(DOC_OPTIONS)
            .call()
            .content();
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

package org.dromara.common.ai.service;

import com.alibaba.cloud.ai.advisor.DashScopeDocumentAnalysisAdvisor;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
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
    private final DashScopeDocumentAnalysisAdvisor documentAdvisor;

    public AiChatService(EmbeddingModel embeddingModel,
                         ChatClient.Builder chatClientBuilder,
                         @Value("${spring.ai.dashscope.api-key}") String apiKey) {
        this.embeddingModel = embeddingModel;
        this.documentAdvisor = new DashScopeDocumentAnalysisAdvisor(new SimpleApiKey(apiKey));
        // 不注册为 defaultAdvisors，避免普通 chat 请求触发文档解析导致 URL 错误
        this.chatClient = chatClientBuilder.build();
    }

    private static final DashScopeChatOptions CHAT_OPTIONS = DashScopeChatOptions.builder()
        .withModel("qwen-plus")
        .withTemperature(0.3)
        .withTopP(0.9)
        .build();

    private static final DashScopeChatOptions GENERATE_OPTIONS = DashScopeChatOptions.builder()
        .withModel("qwen3.5-plus")
        .withTemperature(0.3)
        .withTopP(0.9)
        .build();

    private static final DashScopeChatOptions DOC_OPTIONS = DashScopeChatOptions.builder()
        .withModel("qwen-long-latest")
        .withTemperature(0.3)
        .withMaxToken(4000)
        .withTopP(0.8)
        .build();

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
        String cleanMessage = userMessage == null ? "" : userMessage
            .replaceAll("https?://\\S+", "[链接已省略]")
            .replaceAll("[\\w\\-]+\\.tmp", "[文件已省略]")
            .replaceAll("oss://\\S+", "[文件已省略]")
            .replaceAll("file://\\S+", "[文件已省略]");
        if (cleanMessage.length() > 12000) {
            cleanMessage = cleanMessage.substring(0, 12000) + "\n...(内容已截断)";
        }
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
        return chatClient.prompt()
            .user(prompt)
            .options(GENERATE_OPTIONS)
            .call()
            .content();
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

}

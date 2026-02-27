package org.dromara.common.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
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
@RequiredArgsConstructor
public class AiChatService {

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;

    /**
     * 简单的文本对话
     *
     * @param message 用户消息
     * @return AI 回复
     */
    public String chat(String message) {
        return chatModel.call(message);
    }

    /**
     * 带系统提示词的对话
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @return AI 回复
     */
    public String chat(String systemPrompt, String userMessage) {
        Prompt prompt = new Prompt(List.of(
            new SystemMessage(systemPrompt),
            new UserMessage(userMessage)
        ));
        return chatModel.call(prompt).getResult().getOutput().getText();
    }

    /**
     * 带变量替换的系统提示词对话
     *
     * @param systemPromptTemplate 带变量的系统提示词模板，如：你好{name}，今天是{day}
     * @param variables           变量映射，如：{"name": "张三", "day": "星期一"}
     * @param userMessage         用户消息
     * @return AI 回复
     */
    public String chatWithTemplate(String systemPromptTemplate, Map<String, Object> variables, String userMessage) {
        // 手动替换模板变量
        String resolvedSystemPrompt = systemPromptTemplate;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            resolvedSystemPrompt = resolvedSystemPrompt.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        Prompt prompt = new Prompt(List.of(
            new SystemMessage(resolvedSystemPrompt),
            new UserMessage(userMessage)
        ));
        return chatModel.call(prompt).getResult().getOutput().getText();
    }

    /**
     * 使用 Prompt 对象进行对话
     *
     * @param prompt Spring AI Prompt 对象
     * @return AI 回复
     */
    public String chat(Prompt prompt) {
        return chatModel.call(prompt).getResult().getOutput().getText();
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

}

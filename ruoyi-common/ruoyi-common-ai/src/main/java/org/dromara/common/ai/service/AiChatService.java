package org.dromara.common.ai.service;

import com.alibaba.cloud.ai.advisor.DashScopeDocumentAnalysisAdvisor;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.config.OllamaProperties;
import org.dromara.common.ai.dto.AiModelConfigDto;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;

import org.springframework.util.MimeTypeUtils;
import org.springframework.web.client.RestClient;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private final String dashScopeApiKey;

    /**
     * 本地 Ollama ChatClient（仅在 review.ai.local.enabled=true 时存在），未启用时为 null。
     * 用 setter 注入而非构造器，避免与现有构造器签名冲突。
     */
    private ChatClient ollamaChatClient;
    private OllamaProperties ollamaProperties;

    @Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("ollamaChatModel")
    public void setOllamaChatModel(OllamaChatModel ollamaChatModel) {
        if (ollamaChatModel != null) {
            this.ollamaChatClient = ChatClient.builder(ollamaChatModel).build();
            log.info("[AiChatService] 本地 Ollama 通道已就绪");
        }
    }

    @Autowired(required = false)
    public void setOllamaProperties(OllamaProperties ollamaProperties) {
        this.ollamaProperties = ollamaProperties;
    }

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
        this.dashScopeApiKey = apiKey;
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
        .withMaxToken(32768)
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
     * 对单张图片做 OCR：让视觉模型 (qwen3-vl-plus) 提取图片上的所有文字。
     * <p>
     * 典型场景：扫描件 PDF 每一页转 PNG 后逐页 OCR，把图片"还原"成可比对的文本。
     *
     * @param imageResource 图片资源（PNG/JPG等）
     * @return 提取到的纯文本（按版面顺序）
     */
    public String ocrImage(Resource imageResource) {
        String systemPrompt = "你是一个高精度OCR助手。你只输出图片中的文字内容，不做任何总结、解释或评价。";
        String userPrompt = "请提取图片中的所有文字，要求：\n"
            + "1. 按从上到下、从左到右的版面顺序输出；\n"
            + "2. 保留原始换行、字段标签、表格分隔（用|分隔列即可，不必画完整表格）；\n"
            + "3. 对于印章、手写签字、日期，照样识别并标注（如：[印章: 某某村委会]、[签字: 张三]、[日期: 2026-05-19]）；\n"
            + "4. 模糊字识别不出时用 [?] 占位，不要凭空补字；\n"
            + "5. 直接输出文字，不要任何前缀如 \"识别结果：\"。";
        return chatWithImage(systemPrompt, imageResource, userPrompt);
    }

    /**
     * 长文本对话（qwen-long-latest），用于 OCR 后多文档拼接成纯文本的比对场景。
     * <p>
     * 与 {@link #chat(String, String)} (qwen-plus, 截断 12K) 的区别：
     * - 走 qwen-long 长上下文模型，可吃下数万字符的拼接文本
     * - 不做长度截断，调用方自行控制
     * - 不做 URL/tmp 过滤（OCR 出来的协议正文不含敏感链接）
     */
    public String chatLong(String systemPrompt, String userMessage) {
        final String sys = systemPrompt == null ? "" : systemPrompt;
        final String usr = userMessage == null ? "" : userMessage;
        log.info("[chatLong] systemLen={}, userLen={}", sys.length(), usr.length());
        return callWithRetry(() -> chatClient.prompt()
            .system(sys)
            .user(usr)
            .options(DOC_OPTIONS)
            .call()
            .content());
    }

    /**
     * 长文本对话（本地 Ollama 通道）
     * <p>
     * 与 {@link #chatLong} 相同的入参契约（system + user 纯文本），但走本地大模型：
     * - 强制 think:false（关闭思考模式，否则会浪费上千 token 做内心戏）
     * - 强制 format:json（要求模型输出严格 JSON，便于业务侧 parseObject）
     * - num_ctx 取自 {@link OllamaProperties#getNumCtx()}（默认 16384，4090 24GB 安全水位）
     * - 模型名取自参数（允许业务方在 prompt 模板里指定具体型号），留空则用 properties.model
     * <p>
     * 失败兜底策略：直接抛异常，不自动降级到 DashScope。原因：
     * 1. 选择本地模型本身就是出于"协议正文不上云"的隐私承诺，意外降级会破坏承诺
     * 2. 让上层业务侧感知失败、记录任务为 failed，由人工触发重试更可控
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @param model        模型名（如 qwen3.6:35b-a3b-q4_K_M），为空则用 properties.model
     * @return AI 回复（保证为合法 JSON 字符串）
     */
    public String chatLongLocal(String systemPrompt, String userMessage, String model) {
        if (ollamaChatClient == null) {
            throw new IllegalStateException(
                "本地 Ollama 通道未启用：请检查 review.ai.local.enabled=true 且 OllamaChatModel Bean 已注册");
        }
        final String sys = systemPrompt == null ? "" : systemPrompt;
        final String usr = userMessage == null ? "" : userMessage;
        final String resolvedModel = (model == null || model.isBlank())
            ? ollamaProperties.getModel() : model;

        log.info("[chatLongLocal] model={}, systemLen={}, userLen={}, numCtx={}, numPredict={}",
            resolvedModel, sys.length(), usr.length(),
            ollamaProperties.getNumCtx(), ollamaProperties.getNumPredict());

        OllamaChatOptions options = OllamaChatOptions.builder()
            .model(resolvedModel)
            .temperature(ollamaProperties.getTemperature())
            .topP(ollamaProperties.getTopP())
            .numCtx(ollamaProperties.getNumCtx())
            // 限制输出 token 数，防止本地 MoE 模型退化重复（无限生成 xxx1/xxx2/xxx3...）
            .numPredict(ollamaProperties.getNumPredict())
            // 强制 JSON 输出，模型遵循 schema 的能力会显著提升
            .format("json")
            // 关闭 thinking 模式，节省千级 token 的内心戏
            .disableThinking()
            .build();

        // 不走 callWithRetry：429 限流策略只对 DashScope 适用，本地模型走自身重试
        long start = System.currentTimeMillis();
        String content = ollamaChatClient.prompt()
            // 在 user 末尾追加 /no_think 指令做双保险（部分版本 .think(false) 不生效）
            .system(sys)
            .user(usr.endsWith("/no_think") ? usr : usr + "\n/no_think")
            .options(options)
            .call()
            .content();
        log.info("[chatLongLocal] 耗时 {} ms, 返回长度 {}",
            System.currentTimeMillis() - start, content == null ? 0 : content.length());
        return content;
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
     * 多文档分析（qwen-long 原生支持）
     * <p>
     * 把多个文件分别上传到 DashScope file API，拿到一组 fileid://xxx ，逗号拼到 system message 顶部，
     * 让模型在同一次调用里同时看到所有文档（典型场景：A vs B 双文件比对、合同对照、附件互查）。
     * <p>
     * 实现参考自 DashScopeDocumentAnalysisAdvisor#upload，在此扩展为多文件版本。
     *
     * @param systemPrompt 业务系统提示词（可包含 {output_format} 等占位符；这里不再做占位符替换）
     * @param resources    多个文件资源
     * @param userMessage  用户消息
     * @return AI 回复
     */
    public String chatWithDocuments(String systemPrompt, java.util.List<Resource> resources, String userMessage) {
        if (resources == null || resources.isEmpty()) {
            throw new IllegalArgumentException("resources 不能为空");
        }
        return callWithRetry(() -> {
            // 1) 逐个上传文件，拿 fileid 列表
            java.util.List<String> fileIds = new java.util.ArrayList<>();
            for (Resource r : resources) {
                String fileId = uploadFileForChatWithDocuments(r);
                fileIds.add(fileId);
                log.info("[AiChat] 文档上传完成 → fileid={} (filename={})", fileId, r.getFilename());
            }

            // 2) qwen-long 协议要求：
            //    - system message 内容必须是纯 "fileid://A,fileid://B"，逗号分隔，不能有任何额外字符（含换行）
            //    - 多条 SystemMessage 会被 DashScopeChatModel 合并成一条（用 \n\n 拼），所以也不能多条 system
            //    - 业务提示词必须放到 user message 里
            StringBuilder sysContent = new StringBuilder();
            for (int i = 0; i < fileIds.size(); i++) {
                if (i > 0) sysContent.append(',');
                sysContent.append("fileid://").append(fileIds.get(i));
            }

            // 3) 把 systemPrompt + userMessage 合并成 user 内容
            StringBuilder userContent = new StringBuilder();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                userContent.append(systemPrompt).append("\n\n");
            }
            if (userMessage != null && !userMessage.isBlank()) {
                userContent.append(userMessage);
            }

            log.info("[AiChat] 多文档审核：system={}, userContentLen={}",
                sysContent, userContent.length());

            return chatClient.prompt()
                .system(sysContent.toString())
                .user(userContent.toString())
                .options(DOC_OPTIONS)
                .call()
                .content();
        });
    }

    /**
     * 上传一个 Resource 到 DashScope file API（purpose=file-extract），返回 fileid
     * 复用本类已有的 dashScopeApiKey + multiModalChatClient 暴露的 webClient 实例不太方便，
     * 这里直接用 RestTemplate 风格的 multipart 上传（依赖 spring-web）。
     */
    private String uploadFileForChatWithDocuments(Resource resource) {
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(dashScopeApiKey);
            headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);

            org.springframework.util.LinkedMultiValueMap<String, Object> form = new org.springframework.util.LinkedMultiValueMap<>();
            form.add("file", resource);
            form.add("purpose", "file-extract");

            org.springframework.http.HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity =
                new org.springframework.http.HttpEntity<>(form, headers);

            // 使用与 DashScopeDocumentAnalysisAdvisor 相同的端点
            String uploadUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/files";
            org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
            org.springframework.http.ResponseEntity<java.util.Map> resp =
                rt.postForEntity(uploadUrl, entity, java.util.Map.class);
            if (resp.getBody() == null || resp.getBody().get("id") == null) {
                throw new RuntimeException("文件上传失败：响应无 id 字段，body=" + resp.getBody());
            }
            return resp.getBody().get("id").toString();
        } catch (Exception e) {
            throw new RuntimeException("上传文件到 DashScope 失败: " + e.getMessage(), e);
        }
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

    // ==================== 动态模型配置（review_model_config 驱动） ====================

    private record OllamaCacheEntry(AiModelConfigDto snapshot, OllamaChatModel model) {}
    private record DashScopeCacheEntry(AiModelConfigDto snapshot, DashScopeChatModel model) {}

    /**
     * 缓存：configId → (snapshot, model)。
     * snapshot 用于检测 DB 改动，DTO 不一致则重建实例（无需重启）。
     */
    private final Map<Long, OllamaCacheEntry> ollamaModelCache = new ConcurrentHashMap<>();
    private final Map<Long, DashScopeCacheEntry> dashScopeModelCache = new ConcurrentHashMap<>();

    /**
     * 按配置调用 AI（DB 驱动，编辑后立即生效，无需重启）。
     * 与 chatLong/chatLongLocal 同契约（system + user 纯文本），但模型参数全部来自传入 DTO。
     */
    public String chatWithConfig(AiModelConfigDto config, String systemPrompt, String userMessage) {
        if (config == null || config.getProvider() == null) {
            throw new IllegalArgumentException("AI 模型配置不能为空且 provider 必填");
        }
        String sys = systemPrompt == null ? "" : systemPrompt;
        String usr = userMessage == null ? "" : userMessage;
        long start = System.currentTimeMillis();
        String content = switch (config.getProvider().toLowerCase(Locale.ROOT)) {
            case "ollama" -> chatViaOllama(config, sys, usr);
            case "dashscope" -> chatViaDashScope(config, sys, usr);
            default -> throw new IllegalArgumentException(
                "暂不支持的 provider: " + config.getProvider() + "（仅支持 ollama / dashscope）");
        };
        log.info("[chatWithConfig] code={}, model={}, provider={}, 耗时={}ms, 返回长度={}",
            config.getCode(), config.getModelName(), config.getProvider(),
            System.currentTimeMillis() - start, content == null ? 0 : content.length());
        return content;
    }

    // ==================== Ollama 路由（按 config 缓存实例） ====================

    private String chatViaOllama(AiModelConfigDto cfg, String sys, String usr) {
        OllamaChatModel model = getOrCreateOllamaModel(cfg);

        var optBuilder = OllamaChatOptions.builder()
            .model(cfg.getModelName())
            .temperature(cfg.getTemperature() == null ? 0.2 : cfg.getTemperature().doubleValue())
            .topP(cfg.getTopP() == null ? 0.8 : cfg.getTopP().doubleValue());
        if (cfg.getNumCtx() != null) optBuilder.numCtx(cfg.getNumCtx());
        if (cfg.getNumPredict() != null) optBuilder.numPredict(cfg.getNumPredict());

        // 通过 extraOptions 控制 format / think 等运行时行为
        Map<String, Object> opts = cfg.getExtraOptions();
        if (opts != null) {
            Object fmt = opts.get("format");
            if (fmt instanceof String s && !s.isBlank()) optBuilder.format(s);
            Object think = opts.get("think");
            if (Boolean.FALSE.equals(think)) optBuilder.disableThinking();
        }

        ChatClient client = ChatClient.builder(model).build();
        // 双保险：think:false 在部分 ollama 版本 .disableThinking() 不生效，user 末尾追加 /no_think
        boolean noThink = opts != null && Boolean.FALSE.equals(opts.get("think"));
        String userContent = (noThink && !usr.endsWith("/no_think")) ? usr + "\n/no_think" : usr;

        return client.prompt()
            .system(sys)
            .user(userContent)
            .options(optBuilder.build())
            .call()
            .content();
    }

    private OllamaChatModel getOrCreateOllamaModel(AiModelConfigDto cfg) {
        OllamaCacheEntry cached = ollamaModelCache.get(cfg.getId());
        if (cached != null && isOllamaInstanceCompatible(cached.snapshot(), cfg)) {
            return cached.model();
        }
        synchronized (ollamaModelCache) {
            cached = ollamaModelCache.get(cfg.getId());
            if (cached != null && isOllamaInstanceCompatible(cached.snapshot(), cfg)) {
                return cached.model();
            }
            OllamaChatModel built = buildOllamaModel(cfg);
            ollamaModelCache.put(cfg.getId(), new OllamaCacheEntry(cfg, built));
            log.info("[AiChatService] 构建 Ollama 实例 id={}, code={}, baseUrl={}, model={}",
                cfg.getId(), cfg.getCode(), cfg.getBaseUrl(), cfg.getModelName());
            return built;
        }
    }

    /** 实例级参数（baseUrl/timeout）一致才能复用缓存；模型名 + numCtx 等 per-request 选项不进入判定 */
    private boolean isOllamaInstanceCompatible(AiModelConfigDto a, AiModelConfigDto b) {
        return java.util.Objects.equals(a.getBaseUrl(), b.getBaseUrl())
            && java.util.Objects.equals(a.getTimeoutMs(), b.getTimeoutMs());
    }

    private OllamaChatModel buildOllamaModel(AiModelConfigDto cfg) {
        if (cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("Ollama 配置缺少 baseUrl: " + cfg.getCode());
        }
        long readTimeout = cfg.getTimeoutMs() == null ? 300000L : cfg.getTimeoutMs();
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(30));
        rf.setReadTimeout(Duration.ofMillis(readTimeout));
        RestClient.Builder rcb = RestClient.builder().requestFactory(rf);
        OllamaApi api = OllamaApi.builder()
            .baseUrl(cfg.getBaseUrl())
            .restClientBuilder(rcb)
            .build();
        return OllamaChatModel.builder()
            .ollamaApi(api)
            .modelManagementOptions(ModelManagementOptions.defaults())
            .build();
    }

    // ==================== DashScope 路由（按 config 缓存实例） ====================

    private String chatViaDashScope(AiModelConfigDto cfg, String sys, String usr) {
        DashScopeChatModel model = getOrCreateDashScopeModel(cfg);
        var optBuilder = DashScopeChatOptions.builder()
            .withModel(cfg.getModelName())
            .withTemperature(cfg.getTemperature() == null ? 0.3 : cfg.getTemperature().doubleValue())
            .withTopP(cfg.getTopP() == null ? 0.8 : cfg.getTopP().doubleValue());
        if (cfg.getMaxTokens() != null) {
            optBuilder.withMaxToken(cfg.getMaxTokens());
        }
        Map<String, Object> opts = cfg.getExtraOptions();
        if (opts != null) {
            Object inc = opts.get("incrementalOutput");
            if (Boolean.TRUE.equals(inc)) {
                optBuilder.withIncrementalOutput(true);
            }
            Object multi = opts.get("multiModel");
            if (Boolean.TRUE.equals(multi)) {
                optBuilder.withMultiModel(true);
            }
            // Prompt Cache：审核场景 system+rules+knowledge 占 60%+，重复任务能命中缓存大幅降低延迟和成本。
            // SDK 1.1.0.0 没暴露 enable_cache 字段，通过 X-DashScope-Cache header 触达后端。
            // 文档：https://help.aliyun.com/zh/model-studio/context-cache
            Object cache = opts.get("enableCache");
            if (Boolean.TRUE.equals(cache)) {
                optBuilder.withHttpHeaders(java.util.Map.of("X-DashScope-Cache", "enable"));
            }
        }
        ChatClient client = ChatClient.builder(model).build();
        try {
            return callWithRetry(() -> client.prompt()
                .system(sys)
                .user(usr)
                .options(optBuilder.build())
                .call()
                .content());
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            // DashScope 模型名/端点不匹配时返回 "url error"，原始报错并不友好；
            // 不做启发式判断（模型种类太多，猜测反而误导），直接把关键上下文打出来让用户自己判断
            if (msg.contains("url error") || msg.contains("InvalidParameter")) {
                boolean multiModel = opts != null && Boolean.TRUE.equals(opts.get("multiModel"));
                throw new RuntimeException(String.format(
                    "DashScope 调用失败 [code=%s, model=%s, multiModel=%s]。"
                        + "通常是模型名与接口类型不匹配：多模态模型(如 qwen-vl-plus / qwen3-vl-plus / qwen3.6-plus)需开启 multiModel；"
                        + "纯文本模型(qwen-plus / qwen-long / qwen-max)需关闭 multiModel。"
                        + "请去「AI模型配置」检查。原始错误: %s",
                    cfg.getCode(), cfg.getModelName(), multiModel, msg), e);
            }
            throw e;
        }
    }

    private DashScopeChatModel getOrCreateDashScopeModel(AiModelConfigDto cfg) {
        DashScopeCacheEntry cached = dashScopeModelCache.get(cfg.getId());
        if (cached != null && isDashScopeInstanceCompatible(cached.snapshot(), cfg)) {
            return cached.model();
        }
        synchronized (dashScopeModelCache) {
            cached = dashScopeModelCache.get(cfg.getId());
            if (cached != null && isDashScopeInstanceCompatible(cached.snapshot(), cfg)) {
                return cached.model();
            }
            DashScopeChatModel built = buildDashScopeModel(cfg);
            dashScopeModelCache.put(cfg.getId(), new DashScopeCacheEntry(cfg, built));
            log.info("[AiChatService] 构建 DashScope 实例 id={}, code={}, model={}",
                cfg.getId(), cfg.getCode(), cfg.getModelName());
            return built;
        }
    }

    private boolean isDashScopeInstanceCompatible(AiModelConfigDto a, AiModelConfigDto b) {
        return java.util.Objects.equals(a.getBaseUrl(), b.getBaseUrl())
            && java.util.Objects.equals(resolveApiKey(a.getApiKey()), resolveApiKey(b.getApiKey()))
            && java.util.Objects.equals(a.getTimeoutMs(), b.getTimeoutMs())
            // multiModel 决定 completionsPath，必须参与缓存键
            && readMultiModel(a) == readMultiModel(b)
            // enableCache 不影响实例本身（只影响请求 header），但留个判定位以便将来切到 body 字段时可控
            && readEnableCache(a) == readEnableCache(b);
    }

    private boolean readMultiModel(AiModelConfigDto cfg) {
        return cfg.getExtraOptions() != null
            && Boolean.TRUE.equals(cfg.getExtraOptions().get("multiModel"));
    }

    private boolean readEnableCache(AiModelConfigDto cfg) {
        return cfg.getExtraOptions() != null
            && Boolean.TRUE.equals(cfg.getExtraOptions().get("enableCache"));
    }

    private DashScopeChatModel buildDashScopeModel(AiModelConfigDto cfg) {
        String apiKey = resolveApiKey(cfg.getApiKey());
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = this.dashScopeApiKey;  // 回退到全局 key
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("DashScope 配置缺少 apiKey: " + cfg.getCode());
        }
        // 关键：注入 timeoutMs 到 RestClient，否则 Jetty 默认 10s 超时，多模态/长文档必然失败
        long readTimeout = cfg.getTimeoutMs() == null || cfg.getTimeoutMs() <= 0 ? 120000L : cfg.getTimeoutMs();
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(30));
        rf.setReadTimeout(Duration.ofMillis(readTimeout));
        RestClient.Builder rcb = RestClient.builder().requestFactory(rf);
        // 多模态走 multimodal-generation 端点，纯文本走默认
        boolean multiModel = cfg.getExtraOptions() != null
            && Boolean.TRUE.equals(cfg.getExtraOptions().get("multiModel"));
        DashScopeApi.Builder builder = DashScopeApi.builder()
            .apiKey(apiKey)
            .restClientBuilder(rcb);
        if (multiModel) {
            builder.completionsPath("/api/v1/services/aigc/multimodal-generation/generation");
        }
        if (cfg.getBaseUrl() != null && !cfg.getBaseUrl().isBlank()) {
            builder.baseUrl(cfg.getBaseUrl());
        }
        return DashScopeChatModel.builder()
            .dashScopeApi(builder.build())
            .build();
    }

    /** apiKey 支持 ${ENV_VAR} 占位符，未设置则返回原值 */
    private String resolveApiKey(String raw) {
        if (raw == null) return null;
        if (raw.startsWith("${") && raw.endsWith("}")) {
            String envName = raw.substring(2, raw.length() - 1);
            String fromEnv = System.getenv(envName);
            return (fromEnv != null && !fromEnv.isBlank()) ? fromEnv : null;
        }
        return raw;
    }

    /**
     * 配置变更时清除缓存（CRUD 后调用）
     */
    public void evictModelCache(Long configId) {
        if (configId == null) {
            ollamaModelCache.clear();
            dashScopeModelCache.clear();
            log.info("[AiChatService] 已清空全部 ChatModel 缓存");
        } else {
            ollamaModelCache.remove(configId);
            dashScopeModelCache.remove(configId);
            log.info("[AiChatService] 清除 ChatModel 缓存 id={}", configId);
        }
    }
}

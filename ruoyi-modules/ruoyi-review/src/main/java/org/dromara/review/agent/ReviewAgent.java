package org.dromara.review.agent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.dto.AiModelConfigDto;
import org.dromara.common.ai.ocr.OcrProviderFactory;
import org.dromara.common.ai.service.AiChatService;
import org.dromara.common.ai.util.PdfTextExtractor;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.review.domain.*;
import org.dromara.review.mapper.*;
import org.dromara.review.service.IReviewModelConfigService;
import org.dromara.review.service.ReviewRagService;
import org.dromara.system.domain.vo.SysOssVo;
import org.dromara.system.service.ISysOssService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 通用审核Agent
 * <p>
 * 根据任务关联的审核标准和规则，自动选择合适的AI模型执行审核。
 * 支持三种模式：
 * - 图片附件（营业执照等）→ qwen-vl 视觉模型
 * - 文档附件（合同、报表等）→ qwen-long 文档分析模型
 * - 纯表单（无附件）→ qwen-plus 文本模型
 * @author Linson
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewAgent {

    private final AiChatService aiChatService;
    private final OcrProviderFactory ocrProviderFactory;
    private final ISysOssService ossService;
    private final ReviewTaskMapper taskMapper;
    private final ReviewTaskFileMapper taskFileMapper;
    private final ReviewTaskStandardMapper taskStandardMapper;
    private final ReviewStandardMapper standardMapper;
    private final ReviewStandardRuleMapper standardRuleMapper;
    private final ReviewStandardFocusMapper standardFocusMapper;
    private final ReviewResultItemMapper resultItemMapper;
    private final ReviewPromptTemplateMapper promptTemplateMapper;
    private final ReviewRagService reviewRagService;
    private final ReviewKnowledgeCaseMapper knowledgeCaseMapper;
    private final ReviewStandardKnowledgeMapper standardKnowledgeMapper;
    private final ReviewKnowledgePatternMapper knowledgePatternMapper;
    private final ReviewKnowledgeMapper knowledgeMapper;
    private final IReviewModelConfigService modelConfigService;

    /** 回调签名共享密钥，外部系统验签必须用相同值 */
    @Value("${review.webhook.secret:}")
    private String webhookSecret;
    /** 单次回调 HTTP 超时（毫秒） */
    @Value("${review.webhook.timeout-ms:10000}")
    private long webhookTimeoutMs;
    /** 失败重试次数（指数退避：2s/5s/10s） */
    @Value("${review.webhook.max-retries:3}")
    private int webhookMaxRetries;

    /**
     * OCR 并发线程池大小（4090 GPU 推理对单图占用一般 30-50%，并发 4 不会掉血）。
     * 实际并发还会被 review_model_config.extraOptions.ocrConcurrency 覆盖。
     */
    @Value("${review.ocr.concurrency:4}")
    private int defaultOcrConcurrency;

    /**
     * 共享 OCR 线程池：daemon + 命名，避免 JVM 退出阻塞 + 排查时一眼看出。
     * volatile 因为 lazy init。
     */
    private volatile ExecutorService ocrExecutor;

    private ExecutorService getOcrExecutor() {
        if (ocrExecutor == null) {
            synchronized (this) {
                if (ocrExecutor == null) {
                    int n = Math.max(1, defaultOcrConcurrency);
                    AtomicInteger seq = new AtomicInteger(0);
                    ThreadFactory tf = r -> {
                        Thread t = new Thread(r, "review-ocr-" + seq.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    };
                    ocrExecutor = Executors.newFixedThreadPool(n, tf);
                    log.info("[ReviewAgent] 初始化 OCR 线程池, size={}", n);
                }
            }
        }
        return ocrExecutor;
    }
    /**
     * 外部系统传入的 filePath 是相对路径时拼接的 base URL（绝对 http(s):// URL 不动）。
     * 例：base=http://192.168.169.47:9004/，外部传 "upload/abc.pdf" → 拼成 http://192.168.169.47:9004/upload/abc.pdf
     * 留空则相对路径下载会直接报错，强制外部系统传完整 URL。
     */
    @Value("${review.external.file-base-url:}")
    private String externalFileBaseUrl;

    @Transactional(rollbackFor = Exception.class)
    public void execute(Long taskId) {
        long startTime = System.currentTimeMillis();

        ReviewTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("审核任务不存在: " + taskId);
        }

        log.info("[ReviewAgent] 开始审核: id={}, name={}, type={}", taskId, task.getTaskName(), task.getTaskType());

        try {
            task.setStatus("reviewing");
            taskMapper.updateById(task);

            // 1. 加载附件
            List<ReviewTaskFile> files = taskFileMapper.selectList(
                Wrappers.<ReviewTaskFile>lambdaQuery().eq(ReviewTaskFile::getTaskId, taskId)
            );

            // 2. 加载标准和规则
            List<Long> standardIds = loadStandardIds(taskId);
            List<ReviewStandardRule> allRules = loadRules(standardIds);

            // 3. 加载提示词模板（按任务类型匹配，找不到则用 general）
            ReviewPromptTemplate template = loadPromptTemplate(task.getTaskType());

            // 3.1 解析模板关联的模型配置（DB驱动，热切换）；为空则走 legacy model_name 路径
            AiModelConfigDto modelConfig = resolveModelConfig(template);

            // 4. 构建知识上下文（RAG 检索案例 + 模式 + 误判）
            String queryText = task.getFormSnapshot() != null ? task.getFormSnapshot() : task.getTaskName();
            String knowledgeContext = reviewRagService.buildEnrichedContext(standardIds, queryText);
            String rulesText = buildRulesText(allRules);

            // 5. 组装 Prompt（抽取为 public 方法，供 Graph 编排节点复用）
            String systemPrompt = buildSystemPrompt(template, rulesText, knowledgeContext, allRules);
            String userPrompt = buildUserPrompt(template, task);

            // 6. 根据附件类型选择 AI 调用方式
            String aiResponse = callAi(files, systemPrompt, userPrompt, template, modelConfig);

            log.info("[ReviewAgent] AI返回结果长度: {}", aiResponse.length());

            // 7. 解析结果 → 保存明细 → 更新状态
            JSONObject result = parseAiResponse(aiResponse);
            saveResultItems(task, result, allRules);

            long duration = System.currentTimeMillis() - startTime;
            String modelUsed = determineModel(files, template, modelConfig);
            updateTaskStatus(task, result, duration, allRules, modelUsed, aiResponse);

            // 8. 写入知识库案例（审核完成后自动沉淀）
            saveToKnowledgeCase(task, standardIds);

            // 9. 聚合问题模式（同类问题出现≥3次自动归纳）
            aggregatePatterns(task, standardIds);

            // 10. 回调外部系统（成功路径）
            try { callbackExternalSystem(task); } catch (Exception ex) { log.warn("[ReviewAgent] 成功路径回调失败 taskId={}", taskId, ex); }

            log.info("[ReviewAgent] 审核完成: taskId={}, passStatus={}, score={}, model={}, 耗时={}ms",
                taskId, task.getPassStatus(), task.getScore(), modelUsed, duration);

        } catch (Exception e) {
            log.error("[ReviewAgent] 审核失败: taskId={}", taskId, e);
            task.setStatus("failed");
            task.setAiSummary("审核执行异常: " + e.getMessage());
            taskMapper.updateById(task);
            // 失败也回调
            try { callbackExternalSystem(task); } catch (Exception ex) { log.warn("回调失败", ex); }
            // 不再 throw e：上层 @Async 调用方除了把异常丢给 SimpleAsyncUncaughtExceptionHandler 多打一遍日志外
            // 没有任何处理；而 throw 会触发 @Transactional(rollbackFor=Exception.class) 回滚，
            // 导致刚刚 setStatus("failed") + updateById 也被一起撤销，DB 永远停在 "reviewing/pending"。
            // 失败应该让事务正常提交，把 failed 状态和 aiSummary（含异常信息）落库供运营排查。
        }
    }

    /**
     * dry-run 执行（评测用）：跑完整 legacy 审核（加载 → RAG → 组装 prompt → 调 AI → 解析）
     * 但不落库/不写知识库/不回调/不改任务状态，返回解析后的结果 JSON。
     * <p>
     * 注意：legacy 路径本就不含误判交叉核对与自校验，这正是与 graph 引擎对比的意义所在。
     * 供双引擎对比端点调用。
     *
     * @param taskId 审核任务 ID
     * @return 审核结果 JSON（含 items / pass_status / summary）；异常时返回带 error 字段的 JSON
     */
    public JSONObject executeDryRun(Long taskId) {
        ReviewTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("审核任务不存在: " + taskId);
        }
        log.info("[ReviewAgent] 开始 legacy dry-run: taskId={}", taskId);
        try {
            List<ReviewTaskFile> files = taskFileMapper.selectList(
                Wrappers.<ReviewTaskFile>lambdaQuery().eq(ReviewTaskFile::getTaskId, taskId));
            List<Long> standardIds = loadStandardIds(taskId);
            List<ReviewStandardRule> allRules = loadRules(standardIds);
            ReviewPromptTemplate template = loadPromptTemplate(task.getTaskType());
            AiModelConfigDto modelConfig = resolveModelConfig(template);

            String queryText = task.getFormSnapshot() != null ? task.getFormSnapshot() : task.getTaskName();
            String knowledgeContext = reviewRagService.buildEnrichedContext(standardIds, queryText);
            String rulesText = buildRulesText(allRules);
            String systemPrompt = buildSystemPrompt(template, rulesText, knowledgeContext, allRules);
            String userPrompt = buildUserPrompt(template, task);

            String aiResponse = callAi(files, systemPrompt, userPrompt, template, modelConfig);
            return parseAiResponse(aiResponse);
        } catch (Exception e) {
            log.error("[ReviewAgent] legacy dry-run 失败: taskId={}", taskId, e);
            JSONObject err = new JSONObject();
            err.put("error", "legacy dry-run 异常: " + e.getMessage());
            return err;
        }
    }

    /**
     * 组装 system prompt：填充规则、知识上下文、字段白名单等占位符。
     * 抽取为 public，供 Graph 编排的 InitialReviewNode 复用，保持与 legacy execute() 完全一致的行为。
     */
    public String buildSystemPrompt(ReviewPromptTemplate template, String rulesText,
                                    String knowledgeContext, List<ReviewStandardRule> allRules) {
        String outputFormat = template.getOutputFormat() != null ? template.getOutputFormat() : "";

        // 构建 field_name 白名单（取规则的 check_field，没有就用 rule_<id>）
        StringBuilder whitelistSb = new StringBuilder();
        for (int i = 0; i < allRules.size(); i++) {
            ReviewStandardRule r = allRules.get(i);
            String field = (r.getCheckField() != null && !r.getCheckField().isBlank())
                ? r.getCheckField() : "rule_" + r.getId();
            whitelistSb.append("  ").append(i + 1).append(". \"").append(field).append("\"");
            if (r.getCategory() != null && !r.getCategory().isBlank()) {
                whitelistSb.append("  // ").append(r.getCategory());
            }
            whitelistSb.append("\n");
        }
        String fieldWhitelist = whitelistSb.toString();
        String ruleCount = String.valueOf(allRules.size());

        // 构建 focus_keywords：从独立的关注表 review_standard_focus 读取（按规则涉及的标准ID）
        // 每条启用的关注要点 = 一个精确提取指令。关注列表已与规则解耦
        java.util.List<String> focusPoints = new java.util.ArrayList<>();
        java.util.LinkedHashSet<Long> stdIdSet = new java.util.LinkedHashSet<>();
        for (ReviewStandardRule r : allRules) {
            if (r.getStandardId() != null) stdIdSet.add(r.getStandardId());
        }
        if (!stdIdSet.isEmpty()) {
            List<ReviewStandardFocus> focuses = standardFocusMapper.selectList(
                Wrappers.<ReviewStandardFocus>lambdaQuery()
                    .in(ReviewStandardFocus::getStandardId, stdIdSet)
                    .ne(ReviewStandardFocus::getStatus, "1")
                    .orderByAsc(ReviewStandardFocus::getSortOrder));
            java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
            for (ReviewStandardFocus f : focuses) {
                if (f.getKeyword() == null || f.getKeyword().isBlank()) continue;
                String point = f.getKeyword().trim();
                if (seen.add(point)) focusPoints.add(point);
            }
        }

        // 动态生成关注列表指令段（从关注表动态读取，不写死在 prompt 模板）
        String focusInstruction = "";
        if (!focusPoints.isEmpty()) {
            StringBuilder pointSb = new StringBuilder();
            for (int i = 0; i < focusPoints.size(); i++) {
                pointSb.append("  ").append(i + 1).append(". 关注要点：\"").append(focusPoints.get(i)).append("\"\n");
            }
            focusInstruction = "\n\n【关注列表 focus_items·严格约束】除 items 外，还需输出 focus_items 数组。\n"
                + "需要逐一定位的关注要点如下：\n" + pointSb
                + "严格规则：\n"
                + "1) 针对上述每个关注要点，在文档中找出【所有】与该要点相关的原文片段——"
                + "若同一要点在文中对应多处不同内容（例如「地址」可能有甲方地址、乙方地址、见证方地址），"
                + "则每一处各输出一条 focus_item（keyword 相同、extracted_value 不同），不要只取其中一处、不要合并\n"
                + "2) 每条 focus_item 含字段：keyword（对应的关注要点原文，从上方列表逐字复制）、"
                + "field_label（该处的简短人类可读标题，如「甲方地址」「乙方地址」，用于区分同一要点的多个实例）、"
                + "extracted_value（文档原文片段）、location（页码/段落定位）、confidence（0~1置信度）\n"
                + "3) extracted_value 必须是文档原文片段，禁止用省略号(...)缩写、禁止改写\n"
                + "4) 若文档中确实找不到某要点对应内容，该要点输出一条，extracted_value 置空字符串\"\"（供前端标记缺漏），不要凭空编造\n"
                + "5) keyword 只允许从上方关注要点列表中选取，禁止自创\n"
                + "6) focus_items 用于辅助人工定位关键内容，不计入审核评分";
        } else {
            focusInstruction = "\n\n【关注列表】focus_items 输出空数组[]即可，无需提取。";
        }

        String basePrompt = template.getSystemPrompt()
            .replace("{rules}", rulesText)
            .replace("{knowledge_context}", knowledgeContext)
            .replace("{output_format}", outputFormat)
            .replace("{field_whitelist}", fieldWhitelist)
            .replace("{rule_count}", ruleCount);
        // 兼容：如果模板里还残留 {focus_categories} 旧占位符则清空（关注已迁出到独立关注表，不再用分类）
        if (basePrompt.contains("{focus_categories}")) {
            basePrompt = basePrompt.replace("{focus_categories}", "（无）");
        }
        return basePrompt + focusInstruction;
    }

    /**
     * 组装 user prompt：填充表单数据。抽取为 public 供 Graph 节点复用。
     */
    public String buildUserPrompt(ReviewPromptTemplate template, ReviewTask task) {
        String outputFormat = template.getOutputFormat() != null ? template.getOutputFormat() : "";
        return template.getUserPrompt()
            .replace("{form_data}", task.getFormSnapshot() != null ? task.getFormSnapshot() : "{}")
            .replace("{output_format}", outputFormat);
    }

    /**
     * 根据附件类型自动选择 AI 调用方式：
     * - 有图片附件 → qwen-vl 视觉模型（当前取第一张）
     * - 有文档附件 → 每份PDF探测文本层，打印件直接抽文本，扫描件逐页OCR；最终用 qwen-long / 本地大模型 做纯文本比对
     * - 无附件 → qwen-plus 文本模型
     * <p>
     * 文档审核场景下，通过 {@code template.modelName} 字段路由：
     * - 留空 / 任意 dashscope 模型名 → 走云端 qwen-long（chatLong）
     * - 以 {@code ollama:} 为前缀（如 {@code ollama:qwen3.6:35b-a3b-q4_K_M}） → 走本地 Ollama（chatLongLocal）
     */
    public String callAi(List<ReviewTaskFile> files, String systemPrompt, String userPrompt,
                          ReviewPromptTemplate template, AiModelConfigDto modelConfig) {
        // legacy 入口：不带预处理缓存，文档每次重新 OCR/抽文（与历史行为一致）
        return callAi(files, systemPrompt, userPrompt, template, modelConfig, null);
    }

    /**
     * 带"文档预处理缓存"的 AI 调用。
     * <p>
     * {@code cachedDocText} 非空时，文档场景直接复用该纯文本，跳过昂贵的下载 + OCR/抽文。
     * 供 Graph 重审循环使用：第一轮预处理产出文本后缓存进 ReviewContext，第二轮重审直接复用，
     * 避免文档内容没变却重复 OCR。为 null 时行为与 legacy 完全一致（每次重新预处理）。
     */
    public String callAi(List<ReviewTaskFile> files, String systemPrompt, String userPrompt,
                          ReviewPromptTemplate template, AiModelConfigDto modelConfig, String cachedDocText) {
        List<ReviewTaskFile> imageFiles = files.stream()
            .filter(f -> isImageFile(f.getFileType())).toList();
        List<ReviewTaskFile> docFiles = files.stream()
            .filter(f -> isDocumentFile(f.getFileType())).toList();

        if (!imageFiles.isEmpty()) {
            log.info("[ReviewAgent] 检测到 {} 张图片附件，进入视觉审核分支", imageFiles.size());
            try {
                // 并发下载所有图片资源（IO 重叠）
                ExecutorService pool = getOcrExecutor();
                List<CompletableFuture<Resource>> dlFutures = new ArrayList<>(imageFiles.size());
                for (ReviewTaskFile f : imageFiles) {
                    dlFutures.add(CompletableFuture.supplyAsync(() -> {
                        try { return downloadFileAsResource(f); }
                        catch (Exception e) { throw new RuntimeException("下载图片失败: " + f.getFileName(), e); }
                    }, pool));
                }
                List<Resource> resources = new ArrayList<>(imageFiles.size());
                for (CompletableFuture<Resource> ft : dlFutures) {
                    resources.add(ft.join());
                }

                // 优先走 model_config（支持任意多模态模型 + 在线热切换）
                if (modelConfig != null
                    && "dashscope".equalsIgnoreCase(modelConfig.getProvider())
                    && modelConfig.getExtraOptions() != null
                    && Boolean.TRUE.equals(modelConfig.getExtraOptions().get("multiModel"))) {
                    log.info("[ReviewAgent] 视觉路由 → model_config: id={}, code={}, model={}, 图片数={}",
                        modelConfig.getId(), modelConfig.getCode(), modelConfig.getModelName(), resources.size());
                    return aiChatService.chatImagesWithConfig(modelConfig, systemPrompt, resources, userPrompt);
                }

                // legacy 兜底：走老的 multiModalChatClient（固定 qwen3-vl-plus，单图）
                if (resources.size() > 1) {
                    log.warn("[ReviewAgent] 模板未配置 multiModel 模型配置，回退 legacy 单图链路，{} 张图片只会用第一张",
                        resources.size());
                }
                return aiChatService.chatWithImage(systemPrompt, resources.get(0), userPrompt);
            } catch (Exception e) {
                log.error("[ReviewAgent] 图片审核失败: {}", e.getMessage(), e);
                throw new RuntimeException("图片审核失败: " + e.getMessage(), e);
            }
        }

        if (!docFiles.isEmpty()) {
            try {
                // 复用缓存的预处理文本（重审场景），否则现做 OCR/抽文
                String docsBlock = (cachedDocText != null)
                    ? cachedDocText
                    : preprocessDocuments(docFiles, template);
                String enrichedUser = docsBlock + "\n=== 审核任务指令 ===\n" + userPrompt;
                if (cachedDocText != null) {
                    log.info("[ReviewAgent] 复用已缓存的文档预处理文本（跳过 OCR），长度={}", docsBlock.length());
                }

                // 优先走 DB 配置（热切换，运营可在线编辑）
                if (modelConfig != null) {
                    String provider = modelConfig.getProvider() == null ? "" : modelConfig.getProvider().toLowerCase();
                    if ("ollama".equals(provider) || "dashscope".equals(provider)) {
                        log.info("[ReviewAgent] 路由到 model_config: id={}, code={}, provider={}, model={}",
                            modelConfig.getId(), modelConfig.getCode(), provider, modelConfig.getModelName());
                        return aiChatService.chatWithConfig(modelConfig, systemPrompt, enrichedUser);
                    }
                    log.warn("[ReviewAgent] model_config provider={} 不支持文档场景，回退到 legacy 路径",
                        modelConfig.getProvider());
                }

                // Legacy 兜底：按 modelName 前缀路由
                String modelName = template.getModelName();
                if (modelName != null && modelName.startsWith("ollama:")) {
                    String localModel = modelName.substring("ollama:".length());
                    log.info("[ReviewAgent] [legacy] 路由到本地 Ollama: {}", localModel);
                    return aiChatService.chatLongLocal(systemPrompt, enrichedUser, localModel);
                }
                return aiChatService.chatLong(systemPrompt, enrichedUser);
            } catch (Exception e) {
                log.error("[ReviewAgent] 文档预处理或审核失败: {}", e.getMessage(), e);
                throw new RuntimeException("文档审核失败: " + e.getMessage(), e);
            }
        }

        // 纯文本场景：DB 配置优先（dashscope 文本走 chatWithConfig，ollama 同样支持）
        if (modelConfig != null) {
            String provider = modelConfig.getProvider() == null ? "" : modelConfig.getProvider().toLowerCase();
            if ("ollama".equals(provider) || "dashscope".equals(provider)) {
                log.info("[ReviewAgent] 无附件，按 model_config 调用: code={}, model={}",
                    modelConfig.getCode(), modelConfig.getModelName());
                return aiChatService.chatWithConfig(modelConfig, systemPrompt, userPrompt);
            }
        }
        log.info("[ReviewAgent] 无附件，使用文本模型 (qwen-plus)");
        return aiChatService.chat(systemPrompt, userPrompt);
    }

    /**
     * 文档预处理：对文档附件并发执行"下载 + 文本层探测 + OCR/抽文"，按原顺序拼成纯文本块。
     * <p>
     * 抽取为 public，使 Graph 编排能把结果缓存到 ReviewContext，重审时复用、跳过重复 OCR。
     * 自包含：内部按文件类型过滤出文档附件，无文档附件时返回空串。
     *
     * @param files    任务全部附件（内部自行过滤文档类）
     * @param template 提示词模板（取 ocrConfigId）
     * @return 拼接后的文档纯文本块（每份文件带【文件N：名】前缀），无文档返回空串
     */
    public String preprocessDocuments(List<ReviewTaskFile> files, ReviewPromptTemplate template) {
        if (files == null || files.isEmpty()) {
            return "";
        }
        List<ReviewTaskFile> docFiles = files.stream()
            .filter(f -> isDocumentFile(f.getFileType())).toList();
        if (docFiles.isEmpty()) {
            return "";
        }
        log.info("[ReviewAgent] 检测到 {} 份文档附件，开始文本层探测 + OCR 预处理 (ocrConfigId={})",
            docFiles.size(), template.getOcrConfigId());
        long preStart = System.currentTimeMillis();
        ExecutorService pool = getOcrExecutor();
        // 双（或多）PDF 并发抽文 + OCR；按原顺序拼装
        List<CompletableFuture<String>> docFutures = new ArrayList<>(docFiles.size());
        for (int i = 0; i < docFiles.size(); i++) {
            final int idx = i;
            final ReviewTaskFile f = docFiles.get(i);
            docFutures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    String text = extractOrOcrPdf(f, template.getOcrConfigId());
                    return "【文件" + (idx + 1) + "："
                        + (f.getFileName() == null ? "未命名" : f.getFileName()) + "】\n"
                        + text + "\n\n";
                } catch (Exception e) {
                    throw new RuntimeException("文件预处理失败 [" + f.getFileName() + "]: " + e.getMessage(), e);
                }
            }, pool));
        }
        StringBuilder docsBlock = new StringBuilder();
        for (CompletableFuture<String> ft : docFutures) {
            docsBlock.append(ft.join());
        }
        log.info("[ReviewAgent] 文档预处理完成，文本总长度={}, 耗时={}ms",
            docsBlock.length(), System.currentTimeMillis() - preStart);
        return docsBlock.toString();
    }

    /**
     * 把一份 PDF 转成纯文本：
     * - 打印件（有文本层）→ PDFBox 直接抽取
     * - 扫描件（无文本层）→ 逐页渲染为 PNG → qwen-vl OCR → 拼接
     * <p>
     * 非 PDF 文档（docx/xlsx 等）目前直接 OCR fallback 走不到，会在调用方报"不支持的文档类型"。
     */
    private String extractOrOcrPdf(ReviewTaskFile taskFile, Long ocrConfigId) throws Exception {
        Resource resource = downloadFileAsResource(taskFile);
        if (!(resource instanceof FileSystemResource)) {
            throw new RuntimeException("文档预处理仅支持本地文件资源，实际类型: " + resource.getClass().getName());
        }
        File pdfFile = ((FileSystemResource) resource).getFile();
        String ext = taskFile.getFileType() == null ? "" : taskFile.getFileType().toLowerCase();
        // Word 类合同（系统打印产物或人工上传）：用 POI 直接抽文本，不走 OCR，PDF 链路保持原状
        if ("docx".equals(ext) || "doc".equals(ext)) {
            String text = extractWordText(pdfFile, ext);
            log.info("[ReviewAgent] {} 为 {} 文档，POI 抽出 {} 字符",
                taskFile.getFileName(), ext, text.length());
            return text;
        }
        if (!"pdf".equals(ext)) {
            throw new RuntimeException("当前文档审核仅支持 PDF / Word（doc, docx），收到: " + ext);
        }

        if (PdfTextExtractor.hasTextLayer(pdfFile)) {
            String text = PdfTextExtractor.extractText(pdfFile);
            log.info("[ReviewAgent] {} 为打印件，PDFBox 抽出 {} 字符", taskFile.getFileName(), text.length());
            return text;
        }

        log.info("[ReviewAgent]  为扫描件，逐页OCR开始 (provider={}, ocrConfigId={})",
            taskFile.getFileName(), ocrProviderFactory.currentProviderName(ocrConfigId), ocrConfigId);
        List<byte[]> pages = PdfTextExtractor.renderPagesToPng(pdfFile);
        if (pages.isEmpty()) return "";

        // 多页并发：每页提交到线程池，按页码原序拼装。4090 上 PaddleOCR 单图占 30~50% GPU，能并发跑
        ExecutorService pool = getOcrExecutor();
        long ocrStart = System.currentTimeMillis();
        List<CompletableFuture<String>> futures = new ArrayList<>(pages.size());
        for (int p = 0; p < pages.size(); p++) {
            final int pageNo = p + 1;
            final byte[] pngBytes = pages.get(p);
            futures.add(CompletableFuture.supplyAsync(() -> {
                ByteArrayResource pageRes = new ByteArrayResource(pngBytes) {
                    @Override
                    public String getFilename() { return "page-" + pageNo + ".png"; }
                };
                long ps = System.currentTimeMillis();
                String pageText = ocrProviderFactory.ocrWithConfig(pageRes, ocrConfigId);
                log.info("[ReviewAgent] {} 第{}页OCR完成，识别 {} 字符, 耗时={}ms",
                    taskFile.getFileName(), pageNo, pageText == null ? 0 : pageText.length(),
                    System.currentTimeMillis() - ps);
                return "--- 第" + pageNo + "页 ---\n" + (pageText == null ? "" : pageText) + "\n";
            }, pool));
        }

        StringBuilder sb = new StringBuilder();
        for (CompletableFuture<String> f : futures) {
            sb.append(f.join());
        }
        log.info("[ReviewAgent] {} OCR 全部完成, 共 {} 页, 总耗时={}ms",
            taskFile.getFileName(), pages.size(), System.currentTimeMillis() - ocrStart);
        return sb.toString();
    }

    /**
     * Word 文档纯文本抽取：docx 走 POI XWPF，doc 走 HWPF。
     * 物管侧 GetPrintPath 产出的合同 docx 是模板系统直接生成的纯文本+表格结构，
     * POI 抽出的文本就足以喂给大模型对比，无需再走 LibreOffice 转 PDF。
     */
    private String extractWordText(File wordFile, String ext) throws Exception {
        if ("docx".equals(ext)) {
            try (FileInputStream fis = new FileInputStream(wordFile);
                 XWPFDocument doc = new XWPFDocument(fis);
                 XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
                String text = extractor.getText();
                return text == null ? "" : text;
            }
        }
        // doc (老版本 Word 二进制格式)
        try (FileInputStream fis = new FileInputStream(wordFile);
             HWPFDocument doc = new HWPFDocument(fis);
             WordExtractor extractor = new WordExtractor(doc)) {
            String text = extractor.getText();
            return text == null ? "" : text;
        }
    }

    private Resource downloadFileAsResource(ReviewTaskFile taskFile) {
        log.info("[ReviewAgent] downloadFileAsResource 入参 ossId={}, fileName={}, fileType={}, filePath={}",
            taskFile.getOssId(), taskFile.getFileName(), taskFile.getFileType(), taskFile.getFilePath());
        // 优先通过 OSS 下载
        if (taskFile.getOssId() != null && taskFile.getOssId() > 0) {
            SysOssVo ossVo = TenantHelper.ignore(() -> ossService.getById(taskFile.getOssId()));
            if (ossVo != null) {
                OssClient storage = OssFactory.instance(ossVo.getService());
                Path tempFile = storage.fileDownload(ossVo.getFileName());
                log.info("[ReviewAgent] 文件已下载到临时路径: {}", tempFile);
                return new FileSystemResource(tempFile.toFile());
            }
        }
        // 通过 filePath URL 直接下载（外部系统传入的文件）
        if (taskFile.getFilePath() != null && !taskFile.getFilePath().isBlank()) {
            String fileUrl = taskFile.getFilePath();
            try {
                if (!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://")) {
                    if (externalFileBaseUrl == null || externalFileBaseUrl.isBlank()) {
                        throw new RuntimeException(
                            "filePath 是相对路径但未配置 review.external.file-base-url：" + fileUrl);
                    }
                    String base = externalFileBaseUrl.endsWith("/")
                        ? externalFileBaseUrl
                        : externalFileBaseUrl + "/";
                    String rel = fileUrl.startsWith("/") ? fileUrl.substring(1) : fileUrl;
                    fileUrl = base + rel;
                }
                // 中文路径要做 percent-encoding，否则 URL.openStream 会 400
                String encodedUrl = encodeUrlPathSegments(fileUrl);
                log.info("[ReviewAgent] 通过URL下载文件: 原始={}, 编码后={}", fileUrl, encodedUrl);
                java.net.URL url = java.net.URI.create(encodedUrl).toURL();
                java.net.URLConnection conn = url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                try (java.io.InputStream in = conn.getInputStream()) {
                    String suffix = ".tmp";
                    int dot = fileUrl.lastIndexOf('.');
                    int q = fileUrl.indexOf('?');
                    if (dot > 0) {
                        suffix = q > dot ? fileUrl.substring(dot, q) : fileUrl.substring(dot);
                    }
                    java.io.File tempFile = java.io.File.createTempFile("review_", suffix);
                    java.nio.file.Files.copy(in, tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    log.info("[ReviewAgent] URL下载完成 → {} ({} bytes)", tempFile, tempFile.length());
                    return new FileSystemResource(tempFile);
                }
            } catch (Exception e) {
                // 不再吞异常，把根因抛出来
                throw new RuntimeException("通过URL下载文件失败: url=" + fileUrl + ", 原因=" + e.getMessage(), e);
            }
        }
        throw new RuntimeException("无法下载文件: ossId=" + taskFile.getOssId()
            + ", fileName=" + taskFile.getFileName() + ", filePath=" + taskFile.getFilePath());
    }

    /**
     * 对 URL 路径段做 percent-encoding（保留 / : ? &），避免中文路径触发 400
     */
    private static String encodeUrlPathSegments(String rawUrl) {
        try {
            int schemeEnd = rawUrl.indexOf("://");
            if (schemeEnd < 0) {
                return rawUrl;
            }
            int pathStart = rawUrl.indexOf('/', schemeEnd + 3);
            if (pathStart < 0) {
                return rawUrl;
            }
            String prefix = rawUrl.substring(0, pathStart);
            String pathAndQuery = rawUrl.substring(pathStart);
            int qIdx = pathAndQuery.indexOf('?');
            String path = qIdx >= 0 ? pathAndQuery.substring(0, qIdx) : pathAndQuery;
            String query = qIdx >= 0 ? pathAndQuery.substring(qIdx) : "";
            StringBuilder sb = new StringBuilder();
            for (String seg : path.split("/", -1)) {
                if (sb.length() > 0 || path.startsWith("/")) sb.append('/');
                sb.append(java.net.URLEncoder.encode(seg, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"));
            }
            // 上面循环会把首个 '/' 也加一次，简化处理：直接按 '/' 拆分
            String encodedPath = java.util.Arrays.stream(path.split("/", -1))
                .map(s -> java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"))
                .reduce((a, b) -> a + "/" + b).orElse("");
            return prefix + encodedPath + query;
        } catch (Exception e) {
            return rawUrl;
        }
    }

    public String determineModel(List<ReviewTaskFile> files, ReviewPromptTemplate template, AiModelConfigDto modelConfig) {
        boolean hasImage = files.stream().anyMatch(f -> isImageFile(f.getFileType()));
        if (hasImage) {
            // 视觉走 chatWithImage，模型固定 qwen3-vl-plus；如果模板的 modelConfig
            // 显式指向了 qwen3-vl-plus，就用它的 modelName 落库便于追溯
            if (modelConfig != null && modelConfig.getModelName() != null
                && modelConfig.getModelName().contains("vl")) {
                return modelConfig.getModelName();
            }
            return "qwen3-vl-plus";
        }
        // DB 配置优先：文档/纯文本场景下 chatWithConfig 使用的就是 modelConfig.modelName
        if (modelConfig != null) {
            return modelConfig.getModelName();
        }
        boolean hasDoc = files.stream().anyMatch(f -> isDocumentFile(f.getFileType()));
        if (hasDoc) {
            String modelName = template.getModelName();
            if (modelName != null && modelName.startsWith("ollama:")) {
                return modelName;
            }
            return "qwen-long";
        }
        if (template.getModelName() != null && !template.getModelName().isBlank()) {
            return template.getModelName();
        }
        return "qwen-plus";
    }

    /**
     * 模板配置了 model_config_id → 取出 enabled DTO；为空或加载失败返回 null，让上层走 legacy 路径
     */
    public AiModelConfigDto resolveModelConfig(ReviewPromptTemplate template) {
        if (template == null || template.getModelConfigId() == null) {
            return null;
        }
        try {
            return modelConfigService.getEnabledDto(template.getModelConfigId());
        } catch (Exception e) {
            log.warn("[ReviewAgent] 加载模型配置失败 templateId={}, modelConfigId={}, err={}，回退 legacy 路径",
                template.getId(), template.getModelConfigId(), e.getMessage());
            return null;
        }
    }

    // ==================== 数据加载 ====================

    public List<Long> loadStandardIds(Long taskId) {
        List<ReviewTaskStandard> taskStandards = taskStandardMapper.selectList(
            Wrappers.<ReviewTaskStandard>lambdaQuery().eq(ReviewTaskStandard::getTaskId, taskId)
        );
        return taskStandards.stream().map(ReviewTaskStandard::getStandardId).collect(Collectors.toList());
    }

    public List<ReviewStandardRule> loadRules(List<Long> standardIds) {
        if (standardIds.isEmpty()) return List.of();

        List<ReviewStandardRule> allRules = standardRuleMapper.selectList(
            Wrappers.<ReviewStandardRule>lambdaQuery()
                .in(ReviewStandardRule::getStandardId, standardIds)
                .ne(ReviewStandardRule::getStatus, "1")
                .orderByAsc(ReviewStandardRule::getSortOrder)
        );

        List<ReviewStandardRule> activeRules = allRules.stream()
            .filter(r -> "0".equals(r.getStatus()))
            .collect(Collectors.toList());

        List<ReviewStandardRule> needRevisionRules = allRules.stream()
            .filter(r -> "2".equals(r.getStatus()))
            .collect(Collectors.toList());

        if (!needRevisionRules.isEmpty()) {
            log.warn("[ReviewAgent] {} 条规则处于待修订状态（误判率过高），仍参与审核但置信度已下调: {}",
                needRevisionRules.size(),
                needRevisionRules.stream().map(r -> "R" + r.getId() + ":" + r.getContent()).collect(Collectors.joining("; ")));
        }

        return allRules;
    }

    public ReviewPromptTemplate loadPromptTemplate(String taskType) {
        ReviewPromptTemplate template = promptTemplateMapper.selectOne(
            Wrappers.<ReviewPromptTemplate>lambdaQuery()
                .eq(ReviewPromptTemplate::getType, taskType)
                .eq(ReviewPromptTemplate::getStatus, "0")
                .last("LIMIT 1")
        );
        if (template == null) {
            template = promptTemplateMapper.selectOne(
                Wrappers.<ReviewPromptTemplate>lambdaQuery()
                    .eq(ReviewPromptTemplate::getType, "general")
                    .eq(ReviewPromptTemplate::getStatus, "0")
                    .last("LIMIT 1")
            );
        }
        if (template == null) {
            throw new RuntimeException("未找到类型为 " + taskType + " 或 general 的提示词模板");
        }
        return template;
    }

    public String buildRulesText(List<ReviewStandardRule> rules) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rules.size(); i++) {
            ReviewStandardRule rule = rules.get(i);
            String severityLabel = switch (rule.getSeverity()) {
                case "must" -> "【严重】";
                case "should" -> "【一般】";
                case "suggest" -> "【提示】";
                default -> "【" + rule.getSeverity() + "】";
            };
            sb.append(i + 1).append(". ").append(severityLabel);
            if (rule.getWeight() != null && rule.getWeight() > 0) {
                sb.append("[权重:").append(rule.getWeight()).append("]");
            }
            sb.append(" ").append(rule.getContent());
            if (rule.getCategory() != null && !rule.getCategory().isBlank()) {
                sb.append(" (分类: ").append(rule.getCategory()).append(")");
            }
            if (rule.getCheckField() != null) {
                sb.append(" (检查字段: ").append(rule.getCheckField()).append(")");
            }
            if ("2".equals(rule.getStatus())) {
                sb.append(" ⚠️此规则误判率较高(")
                    .append(rule.getConfidence() != null ? rule.getConfidence() + "%" : "未知")
                    .append("准确率)，判断时请结合上下文谨慎处理，宁可标记为uncertain也不要误判");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ==================== 结果处理 ====================

    public JSONObject parseAiResponse(String aiResponse) {
        String jsonStr = aiResponse.trim();
        if (jsonStr.startsWith("```")) {
            int start = jsonStr.indexOf("{");
            int end = jsonStr.lastIndexOf("}");
            if (start >= 0 && end > start) {
                jsonStr = jsonStr.substring(start, end + 1);
            }
        }
        // 第1次：严格解析
        try {
            return JSON.parseObject(jsonStr);
        } catch (Exception strictErr) {
            // 第2次：截取最大 {...} 区间再试（处理外围有 markdown / 解释文字）
            int start = aiResponse.indexOf("{");
            int end = aiResponse.lastIndexOf("}");
            String extracted = (start >= 0 && end > start) ? aiResponse.substring(start, end + 1) : jsonStr;
            try {
                return JSON.parseObject(extracted);
            } catch (Exception extractErr) {
                // 第3次：转义字符串内 0x00-0x1F 控制字符
                // 本地 Ollama JSON mode 偶尔会让原始换行/Tab 跑进字符串值里，违反 RFC 8259
                log.warn("[ReviewAgent] JSON 解析失败({})，尝试转义控制字符后重试", extractErr.getMessage());
                String sanitized = escapeControlCharsInJsonStrings(extracted);
                try {
                    return JSON.parseObject(sanitized);
                } catch (Exception sanitizeErr) {
                    // 第4次：抢救截断的 JSON（本地 MoE 模型经常输出超过 num_predict 上限被截）
                    // 思路：从原始响应里逐个抽取完整的 {…} 对象（用栈匹配大括号），凑出一个合法 items 数组
                    log.warn("[ReviewAgent] 转义后仍无法解析({})，尝试从截断 JSON 中抢救 items", sanitizeErr.getMessage());
                    JSONObject salvaged = salvageTruncatedJson(aiResponse);
                    if (salvaged != null) {
                        log.warn("[ReviewAgent] 抢救成功，items 数={}，summary={}",
                            salvaged.getJSONArray("items") == null ? 0 : salvaged.getJSONArray("items").size(),
                            salvaged.getString("summary") == null ? "" : salvaged.getString("summary").substring(0, Math.min(80, salvaged.getString("summary").length())));
                        return salvaged;
                    }
                    int previewLen = Math.min(500, aiResponse.length());
                    log.error("[ReviewAgent] 抢救失败，无法解析。响应长度={}, 前{}字符:\n{}",
                        aiResponse.length(), previewLen, aiResponse.substring(0, previewLen));
                    throw new RuntimeException("无法解析AI审核结果（控制字符转义 + 截断抢救均失败）: "
                        + sanitizeErr.getMessage(), sanitizeErr);
                }
            }
        }
    }

    /**
     * 抢救被截断的 JSON：扫描原始响应，按大括号深度匹配抽出完整 item 对象，
     * 组装出一个最小可用的结果 JSON：{summary, pass_status, score, items}
     * <p>
     * 适用场景：本地模型 num_predict 上限触发，输出在 items 数组中间被切断，
     * 末尾几个 item 不完整。strict parser 拒绝整个文档，但前面 N 个完整的 item
     * 已经包含有价值的审核结论，丢掉太可惜。
     */
    private JSONObject salvageTruncatedJson(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        // 抽取顶层 summary / pass_status / score（这些通常在 JSON 开头，截断前已完整）
        String summary = extractTopLevelString(raw, "summary");
        String passStatus = extractTopLevelString(raw, "pass_status");
        Integer score = extractTopLevelInt(raw, "score");

        // 扫描 items 数组：找到 "items" 后的第一个 [，然后用大括号深度匹配抽出每一个完整的 {...}
        int itemsKey = raw.indexOf("\"items\"");
        if (itemsKey < 0) return null;
        int arrStart = raw.indexOf('[', itemsKey);
        if (arrStart < 0) return null;

        JSONArray items = new JSONArray();
        int depth = 0;
        int objStart = -1;
        boolean inString = false;
        boolean prevBackslash = false;
        for (int i = arrStart + 1; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inString) {
                if (prevBackslash) { prevBackslash = false; }
                else if (c == '\\') { prevBackslash = true; }
                else if (c == '"') { inString = false; }
                continue;
            }
            if (c == '"') { inString = true; continue; }
            if (c == '{') {
                if (depth == 0) objStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) {
                    String objStr = raw.substring(objStart, i + 1);
                    objStr = escapeControlCharsInJsonStrings(objStr);
                    try {
                        items.add(JSON.parseObject(objStr));
                    } catch (Exception ignore) {
                        // 单个 item 解析失败就跳过，不影响其他
                    }
                    objStart = -1;
                }
            } else if (c == ']' && depth == 0) {
                break;
            }
        }

        if (items.isEmpty() && summary == null && passStatus == null && score == null) {
            return null;
        }

        JSONObject result = new JSONObject();
        if (summary != null) result.put("summary", summary);
        if (passStatus != null) result.put("pass_status", passStatus);
        if (score != null) result.put("score", score);
        result.put("items", items);
        // 标记为抢救结果，供上层 aiSummary 追加提示
        result.put("_salvaged", true);
        return result;
    }

    /** 从 JSON 字符串里抽取顶层 string 字段（容忍尾部截断）。失败返回 null。 */
    private String extractTopLevelString(String raw, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(raw);
        if (!m.find()) return null;
        int start = m.end();
        // 找未转义的右引号
        boolean prevBackslash = false;
        for (int i = start; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (prevBackslash) { prevBackslash = false; continue; }
            if (c == '\\') { prevBackslash = true; continue; }
            if (c == '"') {
                return raw.substring(start, i)
                    .replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
            }
        }
        // 没找到右引号 → 截断，返回到末尾的内容
        return raw.substring(start)
            .replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /** 从 JSON 字符串里抽取顶层 int 字段。失败返回 null。 */
    private Integer extractTopLevelInt(String raw, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*(-?\\d+)";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(raw);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignore) { }
        }
        return null;
    }

    /**
     * 把字符串字面值（双引号包围）内的 0x00-0x1F 控制字符替换成 JSON 合法转义。
     * 字符串外的 \n \t \r（用作分隔符空白）保持原样。
     * 处理 \" 等已转义的引号，避免误判字符串边界。
     */
    private static String escapeControlCharsInJsonStrings(String input) {
        StringBuilder sb = new StringBuilder(input.length() + 64);
        boolean inString = false;
        boolean prevBackslash = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (inString) {
                if (prevBackslash) {
                    sb.append(c);
                    prevBackslash = false;
                } else if (c == '\\') {
                    sb.append(c);
                    prevBackslash = true;
                } else if (c == '"') {
                    sb.append(c);
                    inString = false;
                } else if (c < 0x20) {
                    switch (c) {
                        case '\n': sb.append("\\n"); break;
                        case '\r': sb.append("\\r"); break;
                        case '\t': sb.append("\\t"); break;
                        case '\b': sb.append("\\b"); break;
                        case '\f': sb.append("\\f"); break;
                        default: sb.append(String.format("\\u%04x", (int) c)); break;
                    }
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
                if (c == '"') inString = true;
            }
        }
        return sb.toString();
    }

    public void saveResultItems(ReviewTask task, JSONObject result, List<ReviewStandardRule> rules) {
        JSONArray items = result.getJSONArray("items");
        if (items == null || items.isEmpty()) {
            log.warn("[ReviewAgent] AI未返回审核明细项");
            return;
        }

        Set<Long> coveredRuleIds = new HashSet<>();

        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            ReviewResultItem resultItem = new ReviewResultItem();
            resultItem.setTaskId(task.getId());
            resultItem.setSortOrder(i + 1);
            resultItem.setMisjudged("0");
            resultItem.setRawData(item.toJSONString());

            // 通用字段提取（尽力提取，提取不到留空）
            resultItem.setSeverity(item.getString("severity"));
            resultItem.setDescription(item.getString("description"));
            resultItem.setSuggestion(item.getString("suggestion"));
            resultItem.setConfidence(item.getBigDecimal("confidence"));
            resultItem.setLocation(item.getString("location"));
            resultItem.setMatchStatus(item.getString("match_status"));

            // 比对类场景字段（公司审核等）
            resultItem.setFieldName(item.getString("field_name"));
            resultItem.setFieldLabel(item.getString("field_label"));
            resultItem.setFormValue(item.getString("form_value"));
            resultItem.setExtractedValue(item.getString("extracted_value"));

            // 规则命中关联：与提示词白名单生成口径保持一致——
            // 规则有 check_field 时按 check_field 匹配，否则回退按 "rule_<id>" 匹配，
            // 否则无 check_field 的规则（如内容审查规则）永远关联不上，rule_id 为空，
            // 会导致知识库沉淀全部被跳过。
            String fieldName = resultItem.getFieldName();
            if (fieldName != null) {
                rules.stream()
                    .filter(r -> {
                        String field = (r.getCheckField() != null && !r.getCheckField().isBlank())
                            ? r.getCheckField() : "rule_" + r.getId();
                        return fieldName.equals(field);
                    })
                    .findFirst()
                    .ifPresent(r -> {
                        resultItem.setRuleId(r.getId());
                        coveredRuleIds.add(r.getId());
                        r.setHitCount(r.getHitCount() + 1);
                        standardRuleMapper.updateById(r);
                    });
            }

            resultItemMapper.insert(resultItem);
        }

        // 补充 AI 未覆盖的规则，默认标记为通过
        int sortOrder = items.size();
        for (ReviewStandardRule rule : rules) {
            if (!coveredRuleIds.contains(rule.getId())) {
                sortOrder++;
                ReviewResultItem passItem = new ReviewResultItem();
                passItem.setTaskId(task.getId());
                passItem.setRuleId(rule.getId());
                passItem.setFieldName(rule.getCheckField());
                passItem.setFieldLabel(rule.getContent());
                passItem.setSeverity("info");
                passItem.setMatchStatus("matched");
                passItem.setConfidence(new java.math.BigDecimal("100.00"));
                passItem.setDescription("该规则已通过审核，未发现问题");
                passItem.setSuggestion("无问题。");
                passItem.setSortOrder(sortOrder);
                passItem.setMisjudged("0");
                resultItemMapper.insert(passItem);
            }
        }
    }

    public void updateTaskStatus(ReviewTask task, JSONObject result, long duration, List<ReviewStandardRule> allRules, String modelUsed, String aiResponse) {
        task.setStatus("completed");
        task.setPassStatus(mapPassStatus(result.getString("pass_status")));
        task.setAiModel(modelUsed);
        task.setReviewDuration(duration);
        task.setAiSummary(result.getString("summary"));
        task.setResultJson(aiResponse);
        task.setResultMarkdown(result.getString("detail_markdown"));
        task.setTotalRules(allRules.size());

        // 关注列表：AI 输出的 focus_items（各分类文本片段，非问题项）存储到 task
        JSONArray focusItems = result.getJSONArray("focus_items");
        if (focusItems != null && !focusItems.isEmpty()) {
            task.setFocusData(focusItems.toJSONString());
        }

        JSONArray items = result.getJSONArray("items");
        int errorCount = 0, warningCount = 0, infoCount = 0, passCount = 0;
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                JSONObject item = items.getJSONObject(i);
                String status = item.getString("match_status");
                String severity = item.getString("severity");
                if ("matched".equals(status)) {
                    passCount++;
                } else if ("error".equals(severity)) {
                    errorCount++;
                } else if ("warning".equals(severity)) {
                    warningCount++;
                } else if ("info".equals(severity)) {
                    infoCount++;
                }
            }
        }
        task.setErrorCount(errorCount);
        task.setWarningCount(warningCount);
        task.setInfoCount(infoCount);
        // AI 未覆盖的规则视为通过
        int uncoveredCount = allRules.size() - (items != null ? items.size() : 0);
        task.setPassCount(passCount + Math.max(uncoveredCount, 0));
        task.setMisjudgedCount(0);
        task.setScore(calculateWeightedScore(allRules, items));
        taskMapper.updateById(task);

        List<ReviewTaskStandard> taskStandards = taskStandardMapper.selectList(
            Wrappers.<ReviewTaskStandard>lambdaQuery().eq(ReviewTaskStandard::getTaskId, task.getId())
        );
        for (ReviewTaskStandard ts : taskStandards) {
            ReviewStandard standard = standardMapper.selectById(ts.getStandardId());
            if (standard != null) {
                standard.setUseCount(standard.getUseCount() + 1);
                standardMapper.updateById(standard);
            }
        }
    }

    // ==================== 加权评分 ====================

    /**
     * 根据规则权重计算加权得分。
     * 算法：每条规则视为一个评分项，matched 得满分（该规则权重），非 matched 按严重程度扣分。
     * 最终 score = 实际得分 / 总权重 * 100，范围 0~100。
     */
    private int calculateWeightedScore(List<ReviewStandardRule> allRules, JSONArray items) {
        if (allRules.isEmpty()) return 100;

        // 总权重 = 所有规则权重之和
        int totalWeight = allRules.stream()
            .mapToInt(r -> r.getWeight() != null && r.getWeight() > 0 ? r.getWeight() : getDefaultWeight(r.getSeverity()))
            .sum();
        if (totalWeight == 0) return 100;

        // 记录哪些规则被 AI 判定为有问题
        java.util.Set<String> problemFields = new java.util.HashSet<>();
        Map<String, String> fieldSeverity = new java.util.HashMap<>();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                JSONObject item = items.getJSONObject(i);
                String matchStatus = item.getString("match_status");
                String fieldName = item.getString("field_name");
                if (!"matched".equals(matchStatus) && fieldName != null) {
                    problemFields.add(fieldName);
                    fieldSeverity.put(fieldName, item.getString("severity"));
                }
            }
        }

        // 计算得分：通过的规则得满权重，有问题的按严重程度扣分
        int earnedWeight = 0;
        for (ReviewStandardRule rule : allRules) {
            int weight = rule.getWeight() != null && rule.getWeight() > 0 ? rule.getWeight() : getDefaultWeight(rule.getSeverity());
            String checkField = rule.getCheckField();
            if (checkField != null && problemFields.contains(checkField)) {
                String severity = fieldSeverity.get(checkField);
                // error 扣全部权重，warning 扣 60%，info 扣 20%
                double deduction = switch (severity != null ? severity : "error") {
                    case "warning" -> 0.6;
                    case "info" -> 0.2;
                    default -> 1.0;
                };
                earnedWeight += (int) (weight * (1.0 - deduction));
            } else {
                earnedWeight += weight;
            }
        }

        return Math.max(0, Math.min(100, (int) Math.round((double) earnedWeight / totalWeight * 100)));
    }

    private int getDefaultWeight(String severity) {
        return switch (severity != null ? severity : "should") {
            case "must" -> 90;
            case "should" -> 70;
            case "suggest" -> 40;
            default -> 70;
        };
    }

    // ==================== 知识库沉淀 ====================

    public void saveToKnowledgeCase(ReviewTask task, List<Long> standardIds) {
        try {
            if (standardIds.isEmpty()) return;

            // 守卫：避免把"失败/无效"的审核结果写入知识库，否则 RAG 会反复检索到这些反例形成自证预言
            String summary = task.getAiSummary() == null ? "" : task.getAiSummary();
            boolean isInvalidResult = "failed".equalsIgnoreCase(task.getStatus())
                || summary.contains("未提交")
                || summary.contains("无法进行")
                || summary.contains("审核执行异常")
                || summary.contains("无法识别");
            if (isInvalidResult) {
                log.info("[ReviewAgent] 跳过将无效/失败案例写入知识库（taskId={}, status={}, summary={}）",
                    task.getId(), task.getStatus(), summary);
                return;
            }

            // 构建 标准ID → 知识库ID 的映射
            List<ReviewStandardKnowledge> skList = standardKnowledgeMapper.selectList(
                Wrappers.<ReviewStandardKnowledge>lambdaQuery().in(ReviewStandardKnowledge::getStandardId, standardIds)
            );
            if (skList.isEmpty()) return;

            Map<Long, Long> standardToKnowledge = skList.stream()
                .collect(Collectors.toMap(ReviewStandardKnowledge::getStandardId, ReviewStandardKnowledge::getKnowledgeId, (a, b) -> a));

            // 构建 规则ID → 标准ID 的映射
            List<ReviewStandardRule> allRules = standardRuleMapper.selectList(
                Wrappers.<ReviewStandardRule>lambdaQuery().in(ReviewStandardRule::getStandardId, standardIds)
            );
            Map<Long, Long> ruleToStandard = allRules.stream()
                .collect(Collectors.toMap(ReviewStandardRule::getId, ReviewStandardRule::getStandardId, (a, b) -> a));

            // 识别通用标准ID集合
            List<ReviewStandard> standards = standardMapper.selectByIds(standardIds);
            Set<Long> systemStandardIds = standards.stream()
                .filter(s -> "1".equals(s.getIsSystem()))
                .map(ReviewStandard::getId)
                .collect(Collectors.toSet());

            // 查询本次审核的全部 result items
            List<ReviewResultItem> resultItems = resultItemMapper.selectList(
                Wrappers.<ReviewResultItem>lambdaQuery().eq(ReviewResultItem::getTaskId, task.getId())
            );

            // 按知识库分组写入
            // 专用标准：所有 item 都写入（正面/负面案例）
            // 通用标准：只有 mismatched 的 item 才写入（全部通过不浪费资源）
            Map<Long, List<ReviewResultItem>> knowledgeItemsMap = new java.util.HashMap<>();
            for (ReviewResultItem item : resultItems) {
                if (item.getRuleId() == null) continue;
                Long stdId = ruleToStandard.get(item.getRuleId());
                if (stdId == null) continue;
                Long knowledgeId = standardToKnowledge.get(stdId);
                if (knowledgeId == null) continue;

                // 通用标准：跳过通过的 item
                if (systemStandardIds.contains(stdId) && "matched".equals(item.getMatchStatus())) {
                    continue;
                }
                knowledgeItemsMap.computeIfAbsent(knowledgeId, k -> new java.util.ArrayList<>()).add(item);
            }

            // 每个知识库写一条案例
            for (Map.Entry<Long, List<ReviewResultItem>> entry : knowledgeItemsMap.entrySet()) {
                Long knowledgeId = entry.getKey();
                List<ReviewResultItem> items = entry.getValue();

                String itemsSummary = items.stream()
                    .map(i -> (i.getFieldLabel() != null ? i.getFieldLabel() : i.getFieldName()) + ": " + (i.getDescription() != null ? i.getDescription() : ""))
                    .collect(Collectors.joining("; "));

                ReviewKnowledgeCase kcase = new ReviewKnowledgeCase();
                kcase.setKnowledgeId(knowledgeId);
                kcase.setTitle(task.getTaskName());
                kcase.setCaseType("pass".equals(task.getPassStatus()) ? "positive" : "negative");
                kcase.setScenario(itemsSummary.length() > 200 ? itemsSummary.substring(0, 200) + "..." : itemsSummary);
                kcase.setFormData(task.getFormSnapshot());
                kcase.setReviewConclusion(task.getAiSummary());
                kcase.setKeyPoint(itemsSummary.length() > 100 ? itemsSummary.substring(0, 100) : itemsSummary);
                knowledgeCaseMapper.insert(kcase);
            }

            log.info("[ReviewAgent] 审核案例已按规则分别写入 {} 个知识库", knowledgeItemsMap.size());
        } catch (Exception e) {
            log.warn("[ReviewAgent] 写入知识库案例失败（不影响审核结果）: {}", e.getMessage());
        }
    }

    // ==================== 问题模式聚合 ====================

    /**
     * 从历史审核结果中聚合问题模式。
     * 逻辑：统计同一知识库下相同 fieldName + severity 的非通过项出现次数，
     * 达到阈值（3次）时自动创建或更新问题模式。
     */
    public void aggregatePatterns(ReviewTask task, List<Long> standardIds) {
        try {
            if (standardIds.isEmpty()) return;

            List<ReviewStandardKnowledge> skList = standardKnowledgeMapper.selectList(
                Wrappers.<ReviewStandardKnowledge>lambdaQuery().in(ReviewStandardKnowledge::getStandardId, standardIds)
            );
            if (skList.isEmpty()) return;

            Map<Long, Long> standardToKnowledge = skList.stream()
                .collect(Collectors.toMap(ReviewStandardKnowledge::getStandardId, ReviewStandardKnowledge::getKnowledgeId, (a, b) -> a));

            // 构建 规则ID → 标准ID 的映射
            List<ReviewStandardRule> allRules = standardRuleMapper.selectList(
                Wrappers.<ReviewStandardRule>lambdaQuery().in(ReviewStandardRule::getStandardId, standardIds)
            );
            Map<Long, Long> ruleToStandard = allRules.stream()
                .collect(Collectors.toMap(ReviewStandardRule::getId, ReviewStandardRule::getStandardId, (a, b) -> a));

            // 查询本次审核中有问题的项
            List<ReviewResultItem> problemItems = resultItemMapper.selectList(
                Wrappers.<ReviewResultItem>lambdaQuery()
                    .eq(ReviewResultItem::getTaskId, task.getId())
                    .ne(ReviewResultItem::getMatchStatus, "matched")
            );
            if (problemItems.isEmpty()) return;

            // 对每个有问题的字段，统计历史上同字段出现问题的总次数
            for (ReviewResultItem item : problemItems) {
                if (item.getFieldName() == null || item.getRuleId() == null) continue;

                // 通过规则定位到知识库
                Long stdId = ruleToStandard.get(item.getRuleId());
                if (stdId == null) continue;
                Long knowledgeId = standardToKnowledge.get(stdId);
                if (knowledgeId == null) continue;

                long historyCount = resultItemMapper.selectCount(
                    Wrappers.<ReviewResultItem>lambdaQuery()
                        .eq(ReviewResultItem::getFieldName, item.getFieldName())
                        .ne(ReviewResultItem::getMatchStatus, "matched")
                        .eq(ReviewResultItem::getMisjudged, "0")
                );

                if (historyCount < 3) continue;

                // 准确率：同字段历史命中中非误判的占比
                long totalHistory = resultItemMapper.selectCount(
                    Wrappers.<ReviewResultItem>lambdaQuery()
                        .eq(ReviewResultItem::getFieldName, item.getFieldName())
                        .ne(ReviewResultItem::getMatchStatus, "matched")
                );
                java.math.BigDecimal accuracy = totalHistory > 0
                    ? java.math.BigDecimal.valueOf(historyCount * 100.0 / totalHistory)
                        .setScale(2, java.math.RoundingMode.HALF_UP)
                    : java.math.BigDecimal.ZERO;

                // 生成面向用户的模式名（优先用中文字段标签 + 中文严重度）
                String severityLabel = mapSeverityLabel(item.getSeverity());
                String fieldDisplay = (item.getFieldLabel() != null && !item.getFieldLabel().isBlank())
                    ? item.getFieldLabel() : item.getFieldName();
                String patternName = String.format("%s-%s", fieldDisplay, severityLabel);
                // 旧数据兼容：同一 knowledgeId 下曾用 fieldName_severity 作为模式名
                String legacyPatternName = item.getFieldName() + "_" + (item.getSeverity() != null ? item.getSeverity() : "error");

                ReviewKnowledgePattern existing = knowledgePatternMapper.selectOne(
                    Wrappers.<ReviewKnowledgePattern>lambdaQuery()
                        .eq(ReviewKnowledgePattern::getKnowledgeId, knowledgeId)
                        .and(w -> w.eq(ReviewKnowledgePattern::getPatternName, patternName)
                            .or().eq(ReviewKnowledgePattern::getPatternName, legacyPatternName))
                        .last("LIMIT 1")
                );

                String description = String.format("字段「%s」频繁出现%s级别问题，已累计 %d 次",
                    fieldDisplay, severityLabel, historyCount);

                if (existing != null) {
                    existing.setPatternName(patternName);
                    existing.setDescription(description);
                    existing.setFrequency((int) historyCount);
                    existing.setAccuracy(accuracy);
                    if (existing.getSolution() == null || existing.getSolution().isBlank()) {
                        existing.setSolution(item.getSuggestion());
                    }
                    knowledgePatternMapper.updateById(existing);
                } else {
                    ReviewKnowledgePattern pattern = new ReviewKnowledgePattern();
                    pattern.setKnowledgeId(knowledgeId);
                    pattern.setPatternName(patternName);
                    pattern.setDescription(description);
                    pattern.setFrequency((int) historyCount);
                    pattern.setAccuracy(accuracy);
                    pattern.setSolution(item.getSuggestion());
                    knowledgePatternMapper.insert(pattern);
                    log.info("[ReviewAgent] 新增问题模式: {} (频次:{}, 准确率:{}%)", patternName, historyCount, accuracy);
                }
            }

            // 回写每个知识库的整体准确率（所有模式的频次加权平均，仅取 accuracy > 0 的模式）
            for (Long knowledgeId : new java.util.HashSet<>(standardToKnowledge.values())) {
                List<ReviewKnowledgePattern> patterns = knowledgePatternMapper.selectList(
                    Wrappers.<ReviewKnowledgePattern>lambdaQuery()
                        .eq(ReviewKnowledgePattern::getKnowledgeId, knowledgeId)
                        .gt(ReviewKnowledgePattern::getAccuracy, java.math.BigDecimal.ZERO)
                );
                if (!patterns.isEmpty()) {
                    double weightedSum = patterns.stream()
                        .mapToDouble(p -> p.getAccuracy().doubleValue() * (p.getFrequency() == null ? 1 : p.getFrequency()))
                        .sum();
                    double totalFreq = patterns.stream()
                        .mapToDouble(p -> p.getFrequency() == null ? 1 : p.getFrequency())
                        .sum();
                    java.math.BigDecimal knowledgeAccuracy = java.math.BigDecimal.valueOf(weightedSum / totalFreq)
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                    ReviewKnowledge kb = new ReviewKnowledge();
                    kb.setId(knowledgeId);
                    kb.setAccuracy(knowledgeAccuracy);
                    knowledgeMapper.updateById(kb);
                    log.info("[ReviewAgent] 知识库 {} 准确率更新为 {}%", knowledgeId, knowledgeAccuracy);
                }
            }
        } catch (Exception e) {
            log.warn("[ReviewAgent] 聚合问题模式失败（不影响审核结果）: {}", e.getMessage());
        }
    }

    // ==================== 文件类型判断 ====================

    private boolean isImageFile(String fileType) {
        if (fileType == null) return false;
        String lower = fileType.toLowerCase();
        return lower.equals("png") || lower.equals("jpg") || lower.equals("jpeg")
            || lower.equals("webp") || lower.equals("bmp") || lower.equals("image");
    }

    private boolean isDocumentFile(String fileType) {
        if (fileType == null) return false;
        String lower = fileType.toLowerCase();
        return lower.equals("pdf") || lower.equals("docx") || lower.equals("doc")
            || lower.equals("xlsx") || lower.equals("xls") || lower.equals("txt");
    }

    private String mapPassStatus(String aiPassStatus) {
        if (aiPassStatus == null) return "pending";
        return switch (aiPassStatus) {
            case "passed" -> "pass";
            case "rejected" -> "fail";
            case "need_review" -> "pending";
            default -> "pending";
        };
    }

    private String mapSeverityLabel(String severity) {
        if (severity == null) return "异常";
        return switch (severity) {
            case "error" -> "严重";
            case "warning" -> "警告";
            case "info" -> "提示";
            default -> severity;
        };
    }

    // ==================== 外部系统回调 ====================

    /**
     * 审核完成后回调外部系统（替代调用方轮询）。
     * <p>
     * URL 优先级：task.callbackUrl（调用方传入）→ resolveCallbackUrl(sourceType)（兜底硬编码）。
     * 安全：HMAC-SHA256 签名 + 时间戳，外部系统验签防伪造/防重放。
     * 重试：失败时指数退避（2s/5s/10s），最多 3 次。
     * Payload：含 status/passStatus/score/errorCount/warningCount/infoCount/aiSummary/items。
     */
    public void callbackExternalSystem(ReviewTask task) {
        if (task.getSourceType() == null || task.getSourceId() == null) {
            return;
        }
        String callbackUrl = task.getCallbackUrl();
        if (callbackUrl == null || callbackUrl.isBlank()) {
            log.debug("[ReviewAgent] 任务 {} 未传 callbackUrl，跳过外部回调（外部系统应在 createTask 时传入回调地址）",
                task.getId());
            return;
        }

        // 拉取审核明细，连同结果一起回调
        List<ReviewResultItem> items = resultItemMapper.selectList(
            Wrappers.<ReviewResultItem>lambdaQuery().eq(ReviewResultItem::getTaskId, task.getId())
                .orderByAsc(ReviewResultItem::getSortOrder));

        JSONObject payload = new JSONObject();
        payload.put("taskId", task.getId());
        payload.put("sourceId", task.getSourceId());
        payload.put("sourceType", task.getSourceType());
        payload.put("status", task.getStatus());
        payload.put("passStatus", task.getPassStatus());
        payload.put("score", task.getScore());
        payload.put("errorCount", task.getErrorCount());
        payload.put("warningCount", task.getWarningCount());
        payload.put("infoCount", task.getInfoCount());
        payload.put("aiSummary", task.getAiSummary());
        payload.put("aiModel", task.getAiModel());
        payload.put("version", task.getVersion());
        payload.put("reviewDuration", task.getReviewDuration());

        JSONArray itemArr = new JSONArray();
        if (items != null) {
            // 规则原文/检查方法回传：按 ruleId 批量查规则，避免逐条查库。供外部系统展示「规则说明」。
            Set<Long> ruleIds = items.stream().map(ReviewResultItem::getRuleId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, ReviewStandardRule> ruleMap = new HashMap<>();
            if (!ruleIds.isEmpty()) {
                for (ReviewStandardRule r : standardRuleMapper.selectByIds(ruleIds)) {
                    ruleMap.put(r.getId(), r);
                }
            }
            for (ReviewResultItem it : items) {
                JSONObject o = new JSONObject();
                o.put("fieldName", it.getFieldName());
                o.put("fieldLabel", it.getFieldLabel());
                o.put("formValue", it.getFormValue());
                o.put("extractedValue", it.getExtractedValue());
                o.put("matchStatus", it.getMatchStatus());
                o.put("severity", it.getSeverity());
                o.put("confidence", it.getConfidence());
                o.put("location", it.getLocation());
                o.put("description", it.getDescription());
                o.put("suggestion", it.getSuggestion());
                // 规则说明：规则原文 + 检查方法（按命中的 ruleId 回填，未命中规则的项留空）
                ReviewStandardRule rule = it.getRuleId() == null ? null : ruleMap.get(it.getRuleId());
                if (rule != null) {
                    o.put("ruleContent", rule.getContent());
                    o.put("checkMethod", rule.getCheckMethod());
                }
                itemArr.add(o);
            }
        }
        payload.put("items", itemArr);

        // 关注列表：AI提取的各分类文本片段（非问题项），已存储在 task.focusData
        if (task.getFocusData() != null && !task.getFocusData().isBlank()) {
            try {
                payload.put("focusItems", JSON.parseArray(task.getFocusData()));
            } catch (Exception e) {
                log.warn("[ReviewAgent] 解析 focusData 失败, taskId={}, err={}", task.getId(), e.getMessage());
            }
        }

        // 关注要点列表：从独立关注表 review_standard_focus 读取（按任务涉及的标准ID）
        // 前端用于生成关注列表骨架 + 缺漏占位；keyword 为前端与 focus_items 关联的主键
        List<Long> stdIds = taskStandardMapper.selectList(
            Wrappers.<ReviewTaskStandard>lambdaQuery().eq(ReviewTaskStandard::getTaskId, task.getId())
        ).stream().map(ReviewTaskStandard::getStandardId).collect(java.util.stream.Collectors.toList());
        if (!stdIds.isEmpty()) {
            List<ReviewStandardFocus> focuses = standardFocusMapper.selectList(
                Wrappers.<ReviewStandardFocus>lambdaQuery()
                    .in(ReviewStandardFocus::getStandardId, stdIds)
                    .ne(ReviewStandardFocus::getStatus, "1")
                    .orderByAsc(ReviewStandardFocus::getSortOrder));
            JSONArray keywordArr = new JSONArray();
            java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
            for (ReviewStandardFocus f : focuses) {
                if (f.getKeyword() == null || f.getKeyword().isBlank()) continue;
                String point = f.getKeyword().trim();
                if (!seen.add(point)) continue;
                JSONObject o = new JSONObject();
                o.put("keyword", point);
                keywordArr.add(o);
            }
            if (!keywordArr.isEmpty()) {
                payload.put("focusKeywords", keywordArr);
            }
        }

        String body = payload.toJSONString();
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = sign(timestamp + "." + body, webhookSecret);

        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(5))
            .build();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create(callbackUrl))
            .timeout(java.time.Duration.ofMillis(webhookTimeoutMs))
            .header("Content-Type", "application/json")
            .header("X-Review-Timestamp", timestamp)
            .header("X-Review-Signature", signature)
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
            .build();

        long[] backoffMs = {2000L, 5000L, 10000L};
        int maxAttempts = Math.max(1, webhookMaxRetries);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                java.net.http.HttpResponse<String> resp = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    log.info("[ReviewAgent] 回调成功 url={}, taskId={}, attempt={}",
                        callbackUrl, task.getId(), attempt);
                    return;
                }
                log.warn("[ReviewAgent] 回调返回非2xx url={}, taskId={}, status={}, body={}",
                    callbackUrl, task.getId(), resp.statusCode(),
                    resp.body() == null ? "" : resp.body().substring(0, Math.min(200, resp.body().length())));
            } catch (Exception e) {
                log.warn("[ReviewAgent] 回调异常 url={}, taskId={}, attempt={}/{}, err={}",
                    callbackUrl, task.getId(), attempt, maxAttempts, e.getMessage());
            }
            if (attempt < maxAttempts) {
                try { Thread.sleep(backoffMs[Math.min(attempt - 1, backoffMs.length - 1)]); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
            }
        }
        log.error("[ReviewAgent] 回调最终失败 url={}, taskId={}, attempts={}", callbackUrl, task.getId(), maxAttempts);
    }

    /**
     * HMAC-SHA256 签名，hex 输出。secret 为空时退化为空串（仅开发环境）。
     */
    private String sign(String message, String secret) {
        if (secret == null || secret.isBlank()) return "";
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(message.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("签名失败", e);
        }
    }
}

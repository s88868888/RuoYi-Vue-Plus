package org.dromara.review.agent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.service.AiChatService;
import org.dromara.common.ai.util.PdfTextExtractor;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.review.domain.*;
import org.dromara.review.mapper.*;
import org.dromara.review.service.ReviewRagService;
import org.dromara.system.domain.vo.SysOssVo;
import org.dromara.system.service.ISysOssService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 通用审核Agent
 * <p>
 * 根据任务关联的审核标准和规则，自动选择合适的AI模型执行审核。
 * 支持三种模式：
 * - 图片附件（营业执照等）→ qwen-vl 视觉模型
 * - 文档附件（合同、报表等）→ qwen-long 文档分析模型
 * - 纯表单（无附件）→ qwen-plus 文本模型
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewAgent {

    private final AiChatService aiChatService;
    private final ISysOssService ossService;
    private final ReviewTaskMapper taskMapper;
    private final ReviewTaskFileMapper taskFileMapper;
    private final ReviewTaskStandardMapper taskStandardMapper;
    private final ReviewStandardMapper standardMapper;
    private final ReviewStandardRuleMapper standardRuleMapper;
    private final ReviewResultItemMapper resultItemMapper;
    private final ReviewPromptTemplateMapper promptTemplateMapper;
    private final ReviewRagService reviewRagService;
    private final ReviewKnowledgeCaseMapper knowledgeCaseMapper;
    private final ReviewStandardKnowledgeMapper standardKnowledgeMapper;
    private final ReviewKnowledgePatternMapper knowledgePatternMapper;
    private final ReviewKnowledgeMapper knowledgeMapper;

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

            // 4. 构建知识上下文（RAG 检索案例 + 模式 + 误判）
            String queryText = task.getFormSnapshot() != null ? task.getFormSnapshot() : task.getTaskName();
            String knowledgeContext = reviewRagService.buildEnrichedContext(standardIds, queryText);
            String rulesText = buildRulesText(allRules);

            // 5. 组装 Prompt
            String outputFormat = template.getOutputFormat() != null ? template.getOutputFormat() : "";
            String ruleCountConstraint = "\n\n【重要约束】本次审核共有 " + allRules.size() + " 条规则，你必须对每一条规则都给出审核结论，" +
                "items 数组中的条目数量必须等于 " + allRules.size() + "。即使某条规则检查通过无问题，也必须返回该条目并标记 match_status 为 matched。不允许遗漏任何规则。\n" +
                "【summary 约束】summary 必须严格基于 items 的实际审核结果来总结，不得与 items 结论矛盾。如果所有 items 的 match_status 都是 matched，summary 不得提及任何不一致或需核查的问题。\n";
            String systemPrompt = template.getSystemPrompt()
                .replace("{rules}", rulesText + ruleCountConstraint)
                .replace("{knowledge_context}", knowledgeContext)
                .replace("{output_format}", outputFormat);

            String userPrompt = template.getUserPrompt()
                .replace("{form_data}", task.getFormSnapshot() != null ? task.getFormSnapshot() : "{}")
                .replace("{output_format}", outputFormat);

            // 6. 根据附件类型选择 AI 调用方式
            String aiResponse = callAi(files, systemPrompt, userPrompt, template);

            log.info("[ReviewAgent] AI返回结果长度: {}", aiResponse.length());

            // 7. 解析结果 → 保存明细 → 更新状态
            JSONObject result = parseAiResponse(aiResponse);
            saveResultItems(task, result, allRules);

            long duration = System.currentTimeMillis() - startTime;
            String modelUsed = determineModel(files, template);
            updateTaskStatus(task, result, duration, allRules, modelUsed, aiResponse);

            // 8. 写入知识库案例（审核完成后自动沉淀）
            saveToKnowledgeCase(task, standardIds);

            // 9. 聚合问题模式（同类问题出现≥3次自动归纳）
            aggregatePatterns(task, standardIds);

            // 10. 回调外部系统
           // callbackExternalSystem(task);

            log.info("[ReviewAgent] 审核完成: taskId={}, passStatus={}, score={}, model={}, 耗时={}ms",
                taskId, task.getPassStatus(), task.getScore(), modelUsed, duration);

        } catch (Exception e) {
            log.error("[ReviewAgent] 审核失败: taskId={}", taskId, e);
            task.setStatus("failed");
            task.setAiSummary("审核执行异常: " + e.getMessage());
            taskMapper.updateById(task);
            // 失败也回调
            try { callbackExternalSystem(task); } catch (Exception ex) { log.warn("回调失败", ex); }
            throw e;
        }
    }

    /**
     * 根据附件类型自动选择 AI 调用方式：
     * - 有图片附件 → qwen-vl 视觉模型（当前取第一张）
     * - 有文档附件 → 每份PDF探测文本层，打印件直接抽文本，扫描件逐页OCR；最终用 qwen-long 做纯文本比对
     * - 无附件 → qwen-plus 文本模型
     */
    private String callAi(List<ReviewTaskFile> files, String systemPrompt, String userPrompt, ReviewPromptTemplate template) {
        List<ReviewTaskFile> imageFiles = files.stream()
            .filter(f -> isImageFile(f.getFileType())).collect(Collectors.toList());
        List<ReviewTaskFile> docFiles = files.stream()
            .filter(f -> isDocumentFile(f.getFileType())).collect(Collectors.toList());

        if (!imageFiles.isEmpty()) {
            ReviewTaskFile imageFile = imageFiles.get(0);
            log.info("[ReviewAgent] 检测到 {} 张图片附件，使用视觉模型 (qwen3-vl-plus), 首张 ossId={}",
                imageFiles.size(), imageFile.getOssId());
            try {
                Resource resource = downloadFileAsResource(imageFile);
                return aiChatService.chatWithImage(systemPrompt, resource, userPrompt);
            } catch (Exception e) {
                log.error("[ReviewAgent] 图片文件下载或分析失败: {}", e.getMessage(), e);
                throw new RuntimeException("图片审核失败: " + e.getMessage(), e);
            }
        }

        if (!docFiles.isEmpty()) {
            log.info("[ReviewAgent] 检测到 {} 份文档附件，开始文本层探测 + OCR 预处理", docFiles.size());
            try {
                StringBuilder docsBlock = new StringBuilder();
                for (int i = 0; i < docFiles.size(); i++) {
                    ReviewTaskFile f = docFiles.get(i);
                    String docText = extractOrOcrPdf(f);
                    docsBlock.append("【文件").append(i + 1).append("：")
                        .append(f.getFileName() == null ? "未命名" : f.getFileName()).append("】\n")
                        .append(docText).append("\n\n");
                }
                // 把"两份/多份文档的纯文本"作为正文，与原本的 userPrompt（业务指令、规则等）拼起来
                String enrichedUser = docsBlock + "\n=== 审核任务指令 ===\n" + userPrompt;
                log.info("[ReviewAgent] 文档预处理完成，文本总长度={}", enrichedUser.length());
                return aiChatService.chatLong(systemPrompt, enrichedUser);
            } catch (Exception e) {
                log.error("[ReviewAgent] 文档预处理或审核失败: {}", e.getMessage(), e);
                throw new RuntimeException("文档审核失败: " + e.getMessage(), e);
            }
        }

        log.info("[ReviewAgent] 无附件，使用文本模型 (qwen-plus)");
        return aiChatService.chat(systemPrompt, userPrompt);
    }

    /**
     * 把一份 PDF 转成纯文本：
     * - 打印件（有文本层）→ PDFBox 直接抽取
     * - 扫描件（无文本层）→ 逐页渲染为 PNG → qwen-vl OCR → 拼接
     * <p>
     * 非 PDF 文档（docx/xlsx 等）目前直接 OCR fallback 走不到，会在调用方报"不支持的文档类型"。
     */
    private String extractOrOcrPdf(ReviewTaskFile taskFile) throws Exception {
        Resource resource = downloadFileAsResource(taskFile);
        if (!(resource instanceof FileSystemResource)) {
            throw new RuntimeException("文档预处理仅支持本地文件资源，实际类型: " + resource.getClass().getName());
        }
        File pdfFile = ((FileSystemResource) resource).getFile();
        String ext = taskFile.getFileType() == null ? "" : taskFile.getFileType().toLowerCase();
        if (!"pdf".equals(ext)) {
            throw new RuntimeException("当前文档审核仅支持 PDF 格式（含打印件和扫描件），收到: " + ext);
        }

        if (PdfTextExtractor.hasTextLayer(pdfFile)) {
            String text = PdfTextExtractor.extractText(pdfFile);
            log.info("[ReviewAgent] {} 为打印件，PDFBox 抽出 {} 字符", taskFile.getFileName(), text.length());
            return text;
        }

        log.info("[ReviewAgent] {} 为扫描件，逐页OCR开始", taskFile.getFileName());
        List<byte[]> pages = PdfTextExtractor.renderPagesToPng(pdfFile);
        StringBuilder sb = new StringBuilder();
        for (int p = 0; p < pages.size(); p++) {
            final int pageNo = p + 1;
            byte[] pngBytes = pages.get(p);
            ByteArrayResource pageRes = new ByteArrayResource(pngBytes) {
                @Override
                public String getFilename() { return "page-" + pageNo + ".png"; }
            };
            String pageText = aiChatService.ocrImage(pageRes);
            sb.append("--- 第").append(pageNo).append("页 ---\n").append(pageText).append("\n");
            log.info("[ReviewAgent] {} 第{}页OCR完成，识别 {} 字符",
                taskFile.getFileName(), pageNo, pageText == null ? 0 : pageText.length());
        }
        return sb.toString();
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
                if (!fileUrl.startsWith("http")) {
                    fileUrl = "http://192.168.169.47:9004/" + fileUrl;
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
            if (schemeEnd < 0) return rawUrl;
            int pathStart = rawUrl.indexOf('/', schemeEnd + 3);
            if (pathStart < 0) return rawUrl;
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

    private String determineModel(List<ReviewTaskFile> files, ReviewPromptTemplate template) {
        boolean hasImage = files.stream().anyMatch(f -> isImageFile(f.getFileType()));
        if (hasImage) return "qwen3-vl-plus";
        boolean hasDoc = files.stream().anyMatch(f -> isDocumentFile(f.getFileType()));
        if (hasDoc) return "qwen-long";
        if (template.getModelName() != null && !template.getModelName().isBlank()) {
            return template.getModelName();
        }
        return "qwen-plus";
    }

    // ==================== 数据加载 ====================

    private List<Long> loadStandardIds(Long taskId) {
        List<ReviewTaskStandard> taskStandards = taskStandardMapper.selectList(
            Wrappers.<ReviewTaskStandard>lambdaQuery().eq(ReviewTaskStandard::getTaskId, taskId)
        );
        return taskStandards.stream().map(ReviewTaskStandard::getStandardId).collect(Collectors.toList());
    }

    private List<ReviewStandardRule> loadRules(List<Long> standardIds) {
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

    private ReviewPromptTemplate loadPromptTemplate(String taskType) {
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

    private String buildRulesText(List<ReviewStandardRule> rules) {
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

    private JSONObject parseAiResponse(String aiResponse) {
        String jsonStr = aiResponse.trim();
        if (jsonStr.startsWith("```")) {
            int start = jsonStr.indexOf("{");
            int end = jsonStr.lastIndexOf("}");
            if (start >= 0 && end > start) {
                jsonStr = jsonStr.substring(start, end + 1);
            }
        }
        try {
            return JSON.parseObject(jsonStr);
        } catch (Exception e) {
            log.warn("[ReviewAgent] AI返回非标准JSON，尝试提取");
            int start = aiResponse.indexOf("{");
            int end = aiResponse.lastIndexOf("}");
            if (start >= 0 && end > start) {
                return JSON.parseObject(aiResponse.substring(start, end + 1));
            }
            throw new RuntimeException("无法解析AI审核结果", e);
        }
    }

    private void saveResultItems(ReviewTask task, JSONObject result, List<ReviewStandardRule> rules) {
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

            // 规则命中关联
            String fieldName = resultItem.getFieldName();
            if (fieldName != null) {
                rules.stream()
                    .filter(r -> fieldName.equals(r.getCheckField()))
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

    private void updateTaskStatus(ReviewTask task, JSONObject result, long duration, List<ReviewStandardRule> allRules, String modelUsed, String aiResponse) {
        task.setStatus("completed");
        task.setPassStatus(mapPassStatus(result.getString("pass_status")));
        task.setAiModel(modelUsed);
        task.setReviewDuration(duration);
        task.setAiSummary(result.getString("summary"));
        task.setResultJson(aiResponse);
        task.setResultMarkdown(result.getString("detail_markdown"));
        task.setTotalRules(allRules.size());

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

    private void saveToKnowledgeCase(ReviewTask task, List<Long> standardIds) {
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
    private void aggregatePatterns(ReviewTask task, List<Long> standardIds) {
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
     * 审核完成后回调外部系统（如商会系统）
     * 根据 sourceType 路由到对应的回调地址
     */
    private void callbackExternalSystem(ReviewTask task) {
        if (task.getSourceType() == null || task.getSourceId() == null) {
            return;
        }
        String callbackUrl = resolveCallbackUrl(task.getSourceType());
        if (callbackUrl == null) {
            return;
        }
        try {
            JSONObject payload = new JSONObject();
            payload.put("taskId", task.getId());
            payload.put("sourceId", task.getSourceId());
            payload.put("sourceType", task.getSourceType());
            payload.put("status", task.getStatus());
            payload.put("passStatus", task.getPassStatus());
            payload.put("score", task.getScore());
            payload.put("aiSummary", task.getAiSummary());
            payload.put("errorCount", task.getErrorCount());
            payload.put("warningCount", task.getWarningCount());
            payload.put("version", task.getVersion());

            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(callbackUrl))
                .timeout(java.time.Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload.toJSONString()))
                .build();
            java.net.http.HttpResponse<String> resp = client.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofString());
            log.info("[ReviewAgent] 回调外部系统成功: url={}, taskId={}, status={}",
                callbackUrl, task.getId(), resp.statusCode());
        } catch (Exception e) {
            log.warn("[ReviewAgent] 回调外部系统失败: sourceType={}, taskId={}, err={}",
                task.getSourceType(), task.getId(), e.getMessage());
        }
    }

    /**
     * 根据 sourceType 解析回调地址
     */
    private String resolveCallbackUrl(String sourceType) {
        return switch (sourceType) {
            case "company" -> "http://127.0.0.1:8000/api/v1/admin/company/review-callback";
            default -> null;
        };
    }
}

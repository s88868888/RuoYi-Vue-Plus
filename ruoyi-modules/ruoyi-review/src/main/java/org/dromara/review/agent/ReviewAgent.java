package org.dromara.review.agent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.service.AiChatService;
import org.dromara.review.domain.*;
import org.dromara.review.mapper.*;
import org.dromara.review.service.ReviewRagService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    private final ReviewTaskMapper taskMapper;
    private final ReviewTaskFileMapper taskFileMapper;
    private final ReviewTaskStandardMapper taskStandardMapper;
    private final ReviewStandardMapper standardMapper;
    private final ReviewStandardRuleMapper standardRuleMapper;
    private final ReviewResultItemMapper resultItemMapper;
    private final ReviewPromptTemplateMapper promptTemplateMapper;
    private final ReviewRagService reviewRagService;

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
            String systemPrompt = template.getSystemPrompt()
                .replace("{rules}", rulesText)
                .replace("{knowledge_context}", knowledgeContext);

            String userPrompt = template.getUserPrompt()
                .replace("{form_data}", task.getFormSnapshot() != null ? task.getFormSnapshot() : "{}");

            // 6. 根据附件类型选择 AI 调用方式
            String aiResponse = callAi(files, systemPrompt, userPrompt, template);

            log.info("[ReviewAgent] AI返回结果长度: {}", aiResponse.length());

            // 7. 解析结果 → 保存明细 → 更新状态
            JSONObject result = parseAiResponse(aiResponse);
            saveResultItems(task, result, allRules);

            long duration = System.currentTimeMillis() - startTime;
            String modelUsed = determineModel(files, template);
            updateTaskStatus(task, result, duration, allRules.size(), modelUsed);

            log.info("[ReviewAgent] 审核完成: taskId={}, passStatus={}, score={}, model={}, 耗时={}ms",
                taskId, task.getPassStatus(), task.getScore(), modelUsed, duration);

        } catch (Exception e) {
            log.error("[ReviewAgent] 审核失败: taskId={}", taskId, e);
            task.setStatus("failed");
            task.setAiSummary("审核执行异常: " + e.getMessage());
            taskMapper.updateById(task);
            throw e;
        }
    }

    /**
     * 根据附件类型自动选择 AI 调用方式
     */
    private String callAi(List<ReviewTaskFile> files, String systemPrompt, String userPrompt, ReviewPromptTemplate template) {
        ReviewTaskFile imageFile = files.stream().filter(f -> isImageFile(f.getFileType())).findFirst().orElse(null);
        ReviewTaskFile docFile = files.stream().filter(f -> isDocumentFile(f.getFileType())).findFirst().orElse(null);

        if (imageFile != null) {
            log.info("[ReviewAgent] 检测到图片附件，使用视觉模型 (qwen3-vl-plus)");
            return aiChatService.chatWithImage(systemPrompt, imageFile.getFilePath(), userPrompt);
        }

        if (docFile != null) {
            log.info("[ReviewAgent] 检测到文档附件，使用文档分析模型 (qwen-long)");
            String fullPrompt = systemPrompt + "\n\n" + userPrompt;
            return aiChatService.chatWithDocumentUrl(docFile.getFilePath(), fullPrompt);
        }

        log.info("[ReviewAgent] 无附件，使用文本模型 (qwen-plus)");
        return aiChatService.chat(systemPrompt, userPrompt);
    }

    private String determineModel(List<ReviewTaskFile> files, ReviewPromptTemplate template) {
        if (template.getModelName() != null && !template.getModelName().isBlank()) {
            return template.getModelName();
        }
        boolean hasImage = files.stream().anyMatch(f -> isImageFile(f.getFileType()));
        if (hasImage) return "qwen3-vl-plus";
        boolean hasDoc = files.stream().anyMatch(f -> isDocumentFile(f.getFileType()));
        if (hasDoc) return "qwen-long";
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
            sb.append(i + 1).append(". ").append(severityLabel).append(" ").append(rule.getContent());
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

        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            ReviewResultItem resultItem = new ReviewResultItem();
            resultItem.setTaskId(task.getId());
            resultItem.setFieldName(item.getString("field_name"));
            resultItem.setFieldLabel(item.getString("field_label"));
            resultItem.setFormValue(item.getString("form_value"));
            resultItem.setExtractedValue(item.getString("extracted_value"));
            resultItem.setMatchStatus(item.getString("match_status"));
            resultItem.setConfidence(item.getBigDecimal("confidence"));
            resultItem.setSeverity(item.getString("severity"));
            resultItem.setDescription(item.getString("description"));
            resultItem.setSuggestion(item.getString("suggestion"));
            resultItem.setLocation(item.getString("location"));
            resultItem.setMisjudged("0");
            resultItem.setSortOrder(i + 1);

            String fieldName = resultItem.getFieldName();
            if (fieldName != null) {
                rules.stream()
                    .filter(r -> fieldName.equals(r.getCheckField()))
                    .findFirst()
                    .ifPresent(r -> {
                        resultItem.setRuleId(r.getId());
                        r.setHitCount(r.getHitCount() + 1);
                        standardRuleMapper.updateById(r);
                    });
            }

            resultItemMapper.insert(resultItem);
        }
    }

    private void updateTaskStatus(ReviewTask task, JSONObject result, long duration, int totalRules, String modelUsed) {
        task.setStatus("completed");
        task.setPassStatus(result.getString("pass_status"));
        task.setScore(result.getInteger("score"));
        task.setAiModel(modelUsed);
        task.setReviewDuration(duration);
        task.setAiSummary(result.getString("summary"));
        task.setTotalRules(totalRules);

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
                } else {
                    infoCount++;
                }
            }
        }
        task.setErrorCount(errorCount);
        task.setWarningCount(warningCount);
        task.setInfoCount(infoCount);
        task.setPassCount(passCount);
        task.setMisjudgedCount(0);
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
}

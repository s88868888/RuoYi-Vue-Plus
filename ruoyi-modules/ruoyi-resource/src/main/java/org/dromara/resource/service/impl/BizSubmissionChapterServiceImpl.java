package org.dromara.resource.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.domain.VectorSearchResult;
import org.dromara.common.ai.service.AiChatService;
import org.dromara.common.ai.service.CompanyVectorService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.common.sse.utils.SseMessageUtils;
import org.dromara.resource.domain.BizBidProject;
import org.dromara.resource.domain.BizBidSubmission;
import org.dromara.resource.domain.BizDocumentConfig;
import org.dromara.resource.domain.BizSubmissionChapter;
import org.dromara.resource.domain.vo.BizSubmissionChapterVo;
import org.dromara.resource.mapper.BizBidProjectMapper;
import org.dromara.resource.mapper.BizBidSubmissionMapper;
import org.dromara.resource.mapper.BizDocumentConfigMapper;
import org.dromara.resource.mapper.BizSubmissionChapterMapper;
import org.dromara.resource.service.BidDocumentVectorService;
import org.dromara.resource.service.IBizSubmissionChapterService;
import org.dromara.resource.service.agent.DocumentParserAgent;
import org.dromara.resource.service.agent.ImageRetrievalAgent;
import org.dromara.system.domain.SysOss;
import org.dromara.system.mapper.SysOssMapper;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 标书章节Service业务层处理
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizSubmissionChapterServiceImpl implements IBizSubmissionChapterService {

    private final BizSubmissionChapterMapper baseMapper;
    private final BizBidSubmissionMapper submissionMapper;
    private final BizBidProjectMapper projectMapper;
    private final BizDocumentConfigMapper documentConfigMapper;
    private final SysOssMapper sysOssMapper;
    private final AiChatService aiChatService;
    private final ImageRetrievalAgent imageRetrievalAgent;
    private final DocumentParserAgent documentParserAgent;
    private final BidDocumentVectorService bidDocumentVectorService;
    private final CompanyVectorService companyVectorService;

    @Override
    public BizSubmissionChapterVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public List<BizSubmissionChapterVo> getChapterTree(Long submissionId, Long documentId) {
        LambdaQueryWrapper<BizSubmissionChapter> lqw = Wrappers.lambdaQuery();
        if (submissionId != null) {
            lqw.eq(BizSubmissionChapter::getBidSubmissionId, submissionId);
        }
        if (documentId != null) {
            lqw.eq(BizSubmissionChapter::getSubmissionDocumentId, documentId);
        }
        lqw.orderByAsc(BizSubmissionChapter::getSortOrder);
        List<BizSubmissionChapterVo> all = baseMapper.selectVoList(lqw);
        return buildTree(all, 0L);
    }

    private List<BizSubmissionChapterVo> buildTree(List<BizSubmissionChapterVo> all, Long parentId) {
        return all.stream()
            .filter(item -> parentId.equals(item.getParentId()))
            .peek(item -> item.setChildren(buildTree(all, item.getId())))
            .collect(Collectors.toList());
    }

    @Override
    public void generateChapter(Long id) {
        BizSubmissionChapter chapter = baseMapper.selectById(id);
        if (chapter == null) {
            return;
        }
        Long userId = LoginHelper.getUserId();
        String tenantId = TenantHelper.getTenantId();
        SpringUtils.getBean(IBizSubmissionChapterService.class)
            .doGenerateChapterContent(id, userId, tenantId);
    }

    @Override
    public void fillTemplate(Long id) {
        BizSubmissionChapter chapter = baseMapper.selectById(id);
        if (chapter == null) {
            return;
        }
        chapter.setGenerationStatus("generating");
        chapter.setGenerationProgress(0);
        baseMapper.updateById(chapter);
        log.info("开始填充模板章节，章节ID: {}", id);
    }

    @Override
    public void saveChapterContent(Long id, String content) {
        BizSubmissionChapter chapter = baseMapper.selectById(id);
        if (chapter == null) {
            return;
        }
        chapter.setChapterContent(content);
        chapter.setGenerationStatus("completed");
        chapter.setGenerationProgress(100);
        baseMapper.updateById(chapter);
    }

    @Override
    public void regenerateChapter(Long id) {
        BizSubmissionChapter chapter = baseMapper.selectById(id);
        if (chapter == null) {
            return;
        }
        chapter.setChapterContent(null);
        chapter.setErrorMessage(null);
        chapter.setGenerationStatus("pending");
        chapter.setGenerationProgress(0);
        baseMapper.updateById(chapter);
        log.info("清空章节内容，准备重新生成，章节ID: {}", id);
        generateChapter(id);
    }

    @Override
    @Async
    public void doGenerateChapterContent(Long chapterId, Long userId, String tenantId) {
        TenantHelper.setDynamic(tenantId);
        try {
            // 1. 查询章节信息
            BizSubmissionChapter chapter = baseMapper.selectById(chapterId);
            if (chapter == null) {
                log.warn("章节不存在，chapterId: {}", chapterId);
                return;
            }

            // 2. 更新状态为生成中，发送 SSE
            chapter.setGenerationStatus("generating");
            chapter.setGenerationProgress(0);
            chapter.setGenerationStartTime(new Date());
            baseMapper.updateById(chapter);
            SseMessageUtils.sendMessage(userId, buildChapterSseMessage("chapter_start", chapterId, "开始生成章节内容: " + chapter.getChapterTitle(), 0));

            // 3. 查询关联信息
            BizBidSubmission submission = submissionMapper.selectById(chapter.getBidSubmissionId());
            if (submission == null) {
                markChapterFailed(chapter, userId, "投标项目不存在");
                return;
            }
            BizBidProject project = projectMapper.selectById(submission.getBidProjectId());
            if (project == null) {
                markChapterFailed(chapter, userId, "招标项目不存在");
                return;
            }

            // 确保招标文件已被向量化到 Milvus
            ensureBidDocVectorized(tenantId, project);

            // 4. 查询文档配置获取 companyId 和 documentType
            BizDocumentConfig documentConfig = documentConfigMapper.selectById(chapter.getSubmissionDocumentId());
            Long companyId = documentConfig != null ? documentConfig.getCompanyId() : null;
            String documentType = documentConfig != null ? documentConfig.getDocumentType() : "complete";

            // 5. 根据章节类型选择生成策略
            boolean isTemplateChapter = "template".equals(chapter.getChapterType());
            String content;
            String prompt;

            if (isTemplateChapter) {
                // template 章节：填充项目信息和公司信息占位符
                SseMessageUtils.sendMessage(userId, buildChapterSseMessage("chapter_start", chapterId, "正在填充模板占位符...", 40));
                content = fillTemplateContent(chapter, submission, project, companyId);

                // 如果模板内容为空，降级为AI生成（保留章节类型不变）
                if (StrUtil.isBlank(content)) {
                    log.warn("模板内容为空，降级为AI生成（保留章节类型不变），chapterId: {}, title: {}", chapterId, chapter.getChapterTitle());
                    SseMessageUtils.sendMessage(userId, buildChapterSseMessage("chapter_start", chapterId, "模板为空，改为AI生成...", 50));
                    ChapterKnowledge knowledge = retrieveKnowledgeForChapter(tenantId, project.getId(), companyId, chapter.getChapterTitle());
                    prompt = buildChapterContentPrompt(chapter, project, documentType, knowledge);
                    content = aiChatService.chatGenerate(prompt);
                }
            } else {
                // generate 章节：双重RAG知识注入 + chatGenerate
                SseMessageUtils.sendMessage(userId, buildChapterSseMessage("chapter_start", chapterId, "正在从知识库检索相关知识...", 20));
                ChapterKnowledge knowledge = retrieveKnowledgeForChapter(tenantId, project.getId(), companyId, chapter.getChapterTitle());
                prompt = buildChapterContentPrompt(chapter, project, documentType, knowledge);
                SseMessageUtils.sendMessage(userId, buildChapterSseMessage("chapter_start", chapterId, "AI正在生成章节内容...", 40));
                content = aiChatService.chatGenerate(prompt);
            }

            // 6. 解析图片占位符，替换为实际图片
            content = imageRetrievalAgent.resolveImagePlaceholders(content, companyId);

            // 6.5. 如果是template类型章节，将招标文件的图片附件添加到文章末尾
            if (isTemplateChapter && project.getAttachments() != null && !project.getAttachments().isBlank()) {
                content = appendProjectAttachments(content, project);
            }

            // 7. 保存内容，更新状态
            Date endTime = new Date();
            chapter.setChapterContent(content);
            chapter.setGenerationStatus("completed");
            chapter.setGenerationProgress(100);
            chapter.setGenerationEndTime(endTime);
            if (chapter.getGenerationStartTime() != null) {
                chapter.setGenerationDuration((int) ((endTime.getTime() - chapter.getGenerationStartTime().getTime()) / 1000));
            }
            chapter.setAiModel("qwen3.5-plus");
            baseMapper.updateById(chapter);

            // 8. 发送成功 SSE
            SseMessageUtils.sendMessage(userId, buildChapterSseMessage("chapter_success", chapterId, "章节内容生成完成: " + chapter.getChapterTitle(), 100));
            log.info("章节内容生成完成，chapterId: {}, title: {}", chapterId, chapter.getChapterTitle());

        } catch (Exception e) {
            log.error("生成章节内容异常，chapterId: {}", chapterId, e);
            // 更新失败状态
            try {
                BizSubmissionChapter failChapter = baseMapper.selectById(chapterId);
                if (failChapter != null) {
                    markChapterFailed(failChapter, userId, "生成失败: " + e.getMessage());
                }
            } catch (Exception ex) {
                log.error("更新失败状态异常", ex);
            }
        } finally {
            TenantHelper.clearDynamic();
        }
    }

    @Override
    public void generateAllChapters(Long submissionId, Long documentConfigId) {
        Long userId = LoginHelper.getUserId();
        String tenantId = TenantHelper.getTenantId();
        SpringUtils.getBean(IBizSubmissionChapterService.class)
            .doGenerateAllChapters(submissionId, documentConfigId, userId, tenantId);
    }

    @Override
    @Async
    public void doGenerateAllChapters(Long submissionId, Long documentConfigId, Long userId, String tenantId) {
        TenantHelper.setDynamic(tenantId);
        try {
            // 1. 查询所有叶子章节（没有子节点的章节）
            LambdaQueryWrapper<BizSubmissionChapter> lqw = Wrappers.lambdaQuery();
            lqw.eq(BizSubmissionChapter::getBidSubmissionId, submissionId);
            lqw.eq(BizSubmissionChapter::getSubmissionDocumentId, documentConfigId);
            lqw.orderByAsc(BizSubmissionChapter::getSortOrder);
            List<BizSubmissionChapter> allChapters = baseMapper.selectList(lqw);

            // 找出叶子节点（parentId 不被其他节点引用的节点）
            java.util.Set<Long> parentIds = allChapters.stream()
                .map(BizSubmissionChapter::getParentId)
                .collect(Collectors.toSet());
            List<BizSubmissionChapter> leafChapters = allChapters.stream()
                .filter(c -> !parentIds.contains(c.getId()))
                .toList();

            int total = leafChapters.size();
            if (total == 0) {
                SseMessageUtils.sendMessage(userId, buildBatchSseMessage("batch_success", "没有需要生成的章节", 0, 0, 100, null));
                return;
            }

            // 2. 发送批量开始 SSE
            SseMessageUtils.sendMessage(userId, buildBatchSseMessage("batch_start", "开始批量生成章节内容", total, 0, 0, null));

            // 3. 查询关联信息（只查一次）
            BizBidSubmission submission = submissionMapper.selectById(submissionId);
            if (submission == null) {
                SseMessageUtils.sendMessage(userId, buildBatchSseMessage("batch_error", "投标项目不存在", 0, 0, 0, null));
                return;
            }
            BizBidProject project = projectMapper.selectById(submission.getBidProjectId());
            if (project == null) {
                SseMessageUtils.sendMessage(userId, buildBatchSseMessage("batch_error", "招标项目不存在", 0, 0, 0, null));
                return;
            }

            // 4.0 确保招标文件已被向量化到 Milvus（若未索引则自动解析并索引）
            ensureBidDocVectorized(tenantId, project);

            // 4. 查询文档配置获取 companyId 和 documentType（只查一次）
            BizDocumentConfig documentConfig = documentConfigMapper.selectById(documentConfigId);
            Long companyId = documentConfig != null ? documentConfig.getCompanyId() : null;
            String documentType = documentConfig != null ? documentConfig.getDocumentType() : "complete";

            // 5. 逐个生成（顺序执行，避免并发限制）
            int current = 0;
            for (BizSubmissionChapter chapter : leafChapters) {
                current++;
                int progress = (int) ((current * 100.0) / total);

                try {
                    // 发送进度 SSE
                    boolean isTemplate = "template".equals(chapter.getChapterType());
                    String progressMsg = isTemplate
                        ? "正在提取格式: " + chapter.getChapterTitle()
                        : "正在生成: " + chapter.getChapterTitle();
                    SseMessageUtils.sendMessage(userId, buildBatchSseMessage("batch_progress",
                        progressMsg, total, current, progress, chapter.getId()));

                    // 更新章节状态
                    chapter.setGenerationStatus("generating");
                    chapter.setGenerationProgress(0);
                    chapter.setGenerationStartTime(new Date());
                    baseMapper.updateById(chapter);

                    // 根据章节类型选择生成策略
                    boolean isTemplateChapter = "template".equals(chapter.getChapterType());
                    String prompt;
                    String content;

                    if (isTemplateChapter) {
                        // template 章节：先尝试 fillTemplateContent 填充模板
                        content = fillTemplateContent(chapter, submission, project, companyId);

                        if (StrUtil.isBlank(content)) {
                            // fillTemplateContent 为空，尝试从 Milvus 检索模板内容
                            List<VectorSearchResult> templateKnowledge = retrieveTemplateKnowledge(
                                tenantId, project.getId(), chapter.getChapterTitle());
                            prompt = buildTemplateExtractPrompt(chapter, project, templateKnowledge);
                            content = callGenerateWithRetry(prompt, userId, chapter.getChapterTitle());
                        }

                        if (StrUtil.isBlank(content) || isEffectivelyEmpty(content)) {
                            // 都为空，降级为AI生成（保留章节类型不变）
                            log.warn("批量生成中模板内容为空，降级为AI生成（保留章节类型不变），chapterId: {}, title: {}", chapter.getId(), chapter.getChapterTitle());
                            ChapterKnowledge knowledge = retrieveKnowledgeForChapter(
                                tenantId, project.getId(), companyId, chapter.getChapterTitle());
                            prompt = buildChapterContentPrompt(chapter, project, documentType, knowledge);
                            content = callGenerateWithRetry(prompt, userId, chapter.getChapterTitle());
                        }
                    } else {
                        // generate 章节：双重RAG知识注入
                        ChapterKnowledge knowledge = retrieveKnowledgeForChapter(
                            tenantId, project.getId(), companyId, chapter.getChapterTitle());
                        prompt = buildChapterContentPrompt(chapter, project, documentType, knowledge);
                        content = callGenerateWithRetry(prompt, userId, chapter.getChapterTitle());
                    }

                    // 解析图片占位符，替换为实际图片
                    content = imageRetrievalAgent.resolveImagePlaceholders(content, companyId);

                    // 如果是template类型章节，将招标文件的图片附件添加到文章末尾
                    if (isTemplateChapter && project.getAttachments() != null && !project.getAttachments().isBlank()) {
                        content = appendProjectAttachments(content, project);
                    }

                    // 保存内容
                    Date endTime = new Date();
                    chapter.setChapterContent(content);
                    chapter.setGenerationStatus("completed");
                    chapter.setGenerationProgress(100);
                    chapter.setGenerationEndTime(endTime);
                    if (chapter.getGenerationStartTime() != null) {
                        chapter.setGenerationDuration((int) ((endTime.getTime() - chapter.getGenerationStartTime().getTime()) / 1000));
                    }
                    chapter.setAiModel("qwen3.5-plus");
                    baseMapper.updateById(chapter);

                    // 发送单章节成功 SSE
                    SseMessageUtils.sendMessage(userId, buildBatchSseMessage("batch_chapter_success",
                        "章节生成完成: " + chapter.getChapterTitle(), total, current, progress, chapter.getId()));

                    // 章节间延迟统一 3 秒（不再需要 10 秒，因为不传文件）
                    if (current < total) {
                        Thread.sleep(3_000);
                    }

                } catch (Exception e) {
                    log.error("批量生成中章节失败, chapterId: {}", chapter.getId(), e);
                    markChapterFailed(chapter, userId, "生成失败: " + e.getMessage());
                    // 单个失败不影响后续章节
                }
            }

            // 6. 全部完成——更新标书配置状态为"已生成内容"，重算投标项目整体进度
            try {
                BizDocumentConfig configUpdate = new BizDocumentConfig();
                configUpdate.setId(documentConfigId);
                configUpdate.setGenerationStatus("content_generated");
                documentConfigMapper.updateById(configUpdate);
                recalculateSubmissionProgress(submissionId);
            } catch (Exception ex) {
                log.warn("更新标书配置状态(content_generated)失败: {}", ex.getMessage());
            }
            SseMessageUtils.sendMessage(userId, buildBatchSseMessage("batch_success", "全部章节生成完成", total, total, 100, null));
            log.info("批量章节内容生成完成，submissionId: {}, total: {}", submissionId, total);

        } catch (Exception e) {
            log.error("批量生成章节内容异常", e);
            SseMessageUtils.sendMessage(userId, buildBatchSseMessage("batch_error", "批量生成异常: " + e.getMessage(), 0, 0, 0, null));
        } finally {
            TenantHelper.clearDynamic();
        }
    }

    /**
     * 章节知识检索结果
     */
    private record ChapterKnowledge(List<VectorSearchResult> bidDocKnowledge,
                                     List<VectorSearchResult> companyKnowledge) {}

    /**
     * 双重RAG检索：招标文件知识 + 企业知识（用于 generate 章节）
     */
    private ChapterKnowledge retrieveKnowledgeForChapter(String tenantId, Long bidProjectId,
                                                          Long companyId, String chapterTitle) {
        List<VectorSearchResult> bidDocKnowledge = List.of();
        List<VectorSearchResult> companyKnowledge = List.of();
        try {
            bidDocKnowledge = bidDocumentVectorService.searchAll(tenantId, bidProjectId, chapterTitle, 10);
            log.info("招标文件知识检索完成，章节: {}, 结果数: {}", chapterTitle, bidDocKnowledge.size());
        } catch (Exception e) {
            log.warn("招标文件知识检索失败，章节: {}, 原因: {}", chapterTitle, e.getMessage());
        }
        try {
            companyKnowledge = companyVectorService.searchCompanyAllData(tenantId, companyId, chapterTitle, 10);
            log.info("企业知识检索完成，章节: {}, 结果数: {}", chapterTitle, companyKnowledge.size());
        } catch (Exception e) {
            log.warn("企业知识检索失败，章节: {}, 原因: {}", chapterTitle, e.getMessage());
        }
        return new ChapterKnowledge(bidDocKnowledge, companyKnowledge);
    }

    /**
     * 模板内容检索：从招标文件向量库检索模板片段和相关正文（用于 template 章节）
     */
    private List<VectorSearchResult> retrieveTemplateKnowledge(String tenantId, Long bidProjectId,
                                                                String chapterTitle) {
        List<VectorSearchResult> results = new ArrayList<>();
        try {
            List<VectorSearchResult> templateResults = bidDocumentVectorService.search(
                tenantId, bidProjectId, chapterTitle, "template", 5);
            results.addAll(templateResults);
            log.info("模板片段检索完成，章节: {}, 结果数: {}", chapterTitle, templateResults.size());
        } catch (Exception e) {
            log.warn("模板片段检索失败，章节: {}, 原因: {}", chapterTitle, e.getMessage());
        }
        try {
            List<VectorSearchResult> contentResults = bidDocumentVectorService.search(
                tenantId, bidProjectId, chapterTitle, "content", 5);
            results.addAll(contentResults);
            log.info("相关正文检索完成，章节: {}, 结果数: {}", chapterTitle, contentResults.size());
        } catch (Exception e) {
            log.warn("相关正文检索失败，章节: {}, 原因: {}", chapterTitle, e.getMessage());
        }
        return results;
    }

    /**
     * 标记章节为失败状态
     */
    private void markChapterFailed(BizSubmissionChapter chapter, Long userId, String errorMsg) {
        chapter.setGenerationStatus("failed");
        chapter.setGenerationProgress(0);
        chapter.setErrorMessage(errorMsg);
        baseMapper.updateById(chapter);
        SseMessageUtils.sendMessage(userId, buildChapterSseMessage("chapter_error", chapter.getId(), errorMsg, 0));
    }

    /**
     * 带重试的 AI 生成调用（qwen3.5-plus，不传文件）
     * 用于 generate 类型章节，token 消耗较小，重试等待时间较短
     */
    private String callGenerateWithRetry(String prompt, Long userId, String chapterTitle) {
        int maxRetries = 3;
        long[] waitSeconds = {10, 20, 40};
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return aiChatService.chatGenerate(prompt);
            } catch (Exception e) {
                String errMsg = e.getMessage() != null ? e.getMessage() : "";
                boolean isRateLimit = errMsg.contains("429") || errMsg.contains("Throttling")
                    || errMsg.contains("quota") || errMsg.contains("rate limit")
                    || errMsg.contains("too quickly");
                if (isRateLimit && attempt < maxRetries) {
                    long waitTime = waitSeconds[attempt];
                    log.warn("AI 生成调用遇到速率限制，{}秒后第{}次重试，章节: {}", waitTime, attempt + 1, chapterTitle);
                    SseMessageUtils.sendMessage(userId, buildBatchSseMessage("batch_progress",
                        "API 限流，等待 " + waitTime + " 秒后重试: " + chapterTitle, 0, 0, 0, null));
                    try {
                        Thread.sleep(waitTime * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试等待被中断", ie);
                    }
                } else {
                    throw e;
                }
            }
        }
        throw new RuntimeException("AI 生成调用重试次数耗尽");
    }

    /**
     * 填充模板内容（支持项目信息占位符）
     */
    private String fillTemplateContent(BizSubmissionChapter chapter, BizBidSubmission submission,
                                       BizBidProject project, Long companyId) {
        String templateSource = chapter.getTemplateSource();
        if (StrUtil.isBlank(templateSource)) {
            return "";
        }

        String content = templateSource;

        // 填充项目信息占位符（括号格式）
        if (project != null) {
            content = content.replace("(项目名称)", project.getProjectName() != null ? project.getProjectName() : "");
            content = content.replace("(招标单位)", project.getBidOrg() != null ? project.getBidOrg() : "");
            content = content.replace("(项目预算)", project.getBudgetAmount() != null ? project.getBudgetAmount().toString() : "");
            content = content.replace("(项目地区)", project.getProjectRegion() != null ? project.getProjectRegion() : "");
        }

        return content;
    }

    /**
     * 将招标文件的图片附件添加到文章末尾（仅用于template类型章节）
     */
    private String appendProjectAttachments(String content, BizBidProject project) {
        if (StrUtil.isBlank(content) || project == null || StrUtil.isBlank(project.getAttachments())) {
            return content;
        }

        StringBuilder sb = new StringBuilder(content);
        String[] attachmentIds = project.getAttachments().split(",");

        for (String ossIdStr : attachmentIds) {
            try {
                Long ossId = Long.parseLong(ossIdStr.trim());
                SysOss oss = sysOssMapper.selectById(ossId);
                if (oss != null && oss.getUrl() != null) {
                    String fileSuffix = oss.getFileSuffix() != null ? oss.getFileSuffix().toLowerCase() : "";
                    if (fileSuffix.matches("jpg|jpeg|png|gif|bmp|webp")) {
                        sb.append("<div style=\"text-align:center;margin-top:20px;\">");
                        sb.append("<img src=\"").append(oss.getUrl()).append("\" ");
                        sb.append("alt=\"").append(oss.getOriginalName() != null ? oss.getOriginalName() : "附件图片").append("\" ");
                        sb.append("style=\"max-width:80%;border:1px solid #eee;border-radius:4px;\" />");
                        sb.append("</div>");
                    }
                }
            } catch (NumberFormatException e) {
                log.warn("解析附件ID失败: {}", ossIdStr);
            }
        }

        return sb.toString();
    }

    /**
     * 构建章节内容生成的 prompt（双重RAG知识注入）
     */
    private String buildChapterContentPrompt(BizSubmissionChapter chapter, BizBidProject project,
                                              String documentType, ChapterKnowledge knowledge) {
        // 文档类型指引
        String typeGuide = switch (documentType != null ? documentType : "complete") {
            case "commercial" -> "你正在生成商务标内容，侧重报价策略、资质展示、业绩亮点、合规性。";
            case "technical" -> "你正在生成技术标内容，侧重技术方案深度、创新性、可行性、实施细节。";
            default -> "你正在生成整本标书内容，需要平衡商务与技术内容。";
        };

        // 招标文件知识段（从 Milvus 检索）
        String bidDocSection = "";
        if (knowledge.bidDocKnowledge() != null && !knowledge.bidDocKnowledge().isEmpty()) {
            bidDocSection = "\n\n【招标文件相关内容（从知识库检索）】\n"
                + knowledge.bidDocKnowledge().stream()
                    .map(VectorSearchResult::getContent)
                    .collect(Collectors.joining("\n---\n"));
        }

        // 企业知识库段（从 Milvus 检索，包含真实人员、资质、业绩等信息）
        String companyKnowledgeSection = "";
        if (knowledge.companyKnowledge() != null && !knowledge.companyKnowledge().isEmpty()) {
            companyKnowledgeSection = "\n\n【企业知识库内容（包含真实的人员、资质、业绩、专利、财务等信息）】\n"
                + knowledge.companyKnowledge().stream()
                    .map(VectorSearchResult::getContent)
                    .collect(Collectors.joining("\n---\n"));
        }

        // 评分标准段
        String scoringSection = "";
        if (StrUtil.isNotBlank(project.getScoringCriteria())) {
            scoringSection = "\n\n【评分标准】\n" + project.getScoringCriteria();
        }

        return String.format("""
            %s

            请为投标项目生成"%s"章节的详细、专业内容。

            【招标项目信息】
            - 项目名称：%s
            - 招标单位：%s
            - 项目类型：%s
            - 预算金额：%s
            - 项目描述：%s

            【章节信息】
            - 章节编号：%s
            - 章节标题：%s
            - 章节层级：第%d级
            - 生成说明：%s
            %s
            %s
            %s
            【生成要求】
            1. 内容必须紧密结合项目信息中的具体要求
            2. 内容充实、逻辑清晰、语言规范
            3. 使用 HTML 格式输出（可使用 <h3>/<h4>/<p>/<ul>/<ol>/<li>/<table>/<tr>/<td>/<th>/<strong>/<em> 等标签）
            4. 包含必要的表格、列表等结构化内容
            5. 针对项目需求和评分标准重点响应
            6. 字数不少于800字
            7. 不要包含章节标题本身（标题会自动添加）
            8. 不要输出 Markdown 格式，请使用 HTML 格式
            9. 突出公司优势和项目经验——必须结合上方【企业知识库内容】中的真实信息
            10. 紧扣招标要求和评分标准

            【图片占位符规则】
            当提到具体的人员、资质证书、业绩项目、专利或财务信息时，在提及处的下一行插入图片占位符：
            - 人员：{{IMAGE:PERSONNEL:人员姓名}}
            - 资质：{{IMAGE:QUALIFICATION:证书名称}}
            - 业绩：{{IMAGE:PERFORMANCE:项目名称}}
            - 专利：{{IMAGE:PATENT:专利名称}}
            - 财务：{{IMAGE:FINANCE:财务报告名称}}
            注意：只在提到具体名称时才插入占位符，占位符中的名称必须使用企业知识库中已有的真实名称，
            不要凭空编造名称，占位符中的名称必须与正文一致。

            请直接输出章节内容。
            """,
            typeGuide,
            chapter.getChapterTitle(),
            project.getProjectName(),
            project.getBidOrg(),
            project.getProjectType(),
            project.getBudgetAmount(),
            project.getProjectDesc() != null ? project.getProjectDesc() : "无",
            chapter.getChapterNo(),
            chapter.getChapterTitle(),
            chapter.getChapterLevel(),
            chapter.getReasonDescription() != null ? chapter.getReasonDescription() : "无",
            bidDocSection,
            companyKnowledgeSection,
            scoringSection
        );
    }

    /**
     * 构建模板章节原文提取 prompt（基于 Milvus 检索到的模板内容）
     */
    private String buildTemplateExtractPrompt(BizSubmissionChapter chapter, BizBidProject project,
                                               List<VectorSearchResult> templateKnowledge) {
        // 拼接检索到的模板片段
        String templateContent = "";
        if (templateKnowledge != null && !templateKnowledge.isEmpty()) {
            templateContent = templateKnowledge.stream()
                .map(VectorSearchResult::getContent)
                .collect(Collectors.joining("\n---\n"));
        }

        return String.format("""
            请根据以下从招标文件中检索到的内容，还原”%s”（章节编号：%s）的规定格式原文。

            【项目信息】
            - 项目名称：%s
            - 招标单位：%s
            - 项目类型：%s
            - 预算金额：%s

            【目标章节】
            - 章节编号：%s
            - 章节标题：%s
            - 章节层级：第%d级

            【招标文件检索内容】
            %s

            【提取要求（必须严格遵守）】
            1. 必须忠实还原招标文件中的原始格式内容，不得改写、扩写、润色
            2. 保留原文中的固定文本、序号、下划线、空白框、日期位、签章位
            3. 保留原有表格结构（使用 HTML table 标签输出，含 <table>/<tr>/<td>/<th>/<thead>/<tbody> 等）
            4. 若章节包含”格式一/格式二/附表/模板/范本/样式”，需完整输出对应内容
            5. 仅输出该章节的格式正文，不要输出章节标题，不要输出解释性文字
            6. 输出格式必须是 HTML（可使用 <p>/<ul>/<ol>/<li>/<table>/<tr>/<td>/<th>/<strong>/<em> 等标签）
            7. 若检索内容中未找到与该章节直接对应的规定格式，请根据章节标题和招标文件的通用要求，生成一个合理的格式框架（含必要的表格、填写区域等），不要返回空内容

            请直接输出提取结果。
        """,
        chapter.getChapterTitle(),
        chapter.getChapterNo(),
        project.getProjectName(),
        project.getBidOrg(),
        project.getProjectType(),
        project.getBudgetAmount(),
        chapter.getChapterNo(),
        chapter.getChapterTitle(),
        chapter.getChapterLevel(),
        StrUtil.isNotBlank(templateContent) ? templateContent : "（未检索到相关模板内容）"
    );
}

/**
 * 判断内容是否实质为空（去除HTML标签和空白后无有效文字）
 */
private boolean isEffectivelyEmpty(String content) {
    if (content == null) return true;
    String text = content.replaceAll("<[^>]*>", "")
        .replaceAll("&nbsp;", " ")
        .replaceAll("\\s+", "")
        .trim();
    return text.length() < 10;
}

/**
 * 确保招标文件已被向量化到 Milvus
 * 若 Milvus 中无数据，则自动解析招标文件并索引内容和模板
 */
private void ensureBidDocVectorized(String tenantId, BizBidProject project) {
    try {
        // 快速检测：查询是否已有 content 类型的向量数据
        List<VectorSearchResult> testResults = bidDocumentVectorService.search(
            tenantId, project.getId(), "招标", "content", 1);
        if (!testResults.isEmpty()) {
            log.info("Milvus向量库已有数据，跳过索引，projectId: {}", project.getId());
            return;
        }

        log.info("Milvus向量库无数据，开始自动解析并索引招标文件，projectId: {}", project.getId());

        // 获取招标文件 OSS 信息
        if (project.getAttachments() == null || project.getAttachments().isBlank()) {
            log.warn("招标项目无附件，无法自动索引，projectId: {}", project.getId());
            return;
        }

        SysOss bidDocOss = null;
        String[] ossIdArr = project.getAttachments().split(",");
        for (String ossIdStr : ossIdArr) {
            try {
                Long ossId = Long.parseLong(ossIdStr.trim());
                bidDocOss = sysOssMapper.selectById(ossId);
                if (bidDocOss != null) break;
            } catch (NumberFormatException ignored) {}
        }

        if (bidDocOss == null) {
            log.warn("未找到招标文件OSS记录，projectId: {}", project.getId());
            return;
        }

        // 解析招标文件
        String fileFormat = bidDocOss.getFileSuffix() != null
            ? bidDocOss.getFileSuffix().replace(".", "") : "";
        DocumentParserAgent.ParseResult parseResult = documentParserAgent.parse(
            bidDocOss.getUrl(), fileFormat, bidDocOss.getOriginalName());

        // 索引到 Milvus（幂等：先清空再写入）
        bidDocumentVectorService.clearProjectData(tenantId, project.getId());
        bidDocumentVectorService.indexDocumentContent(tenantId, project.getId(), parseResult.getContent());
        bidDocumentVectorService.indexTemplates(tenantId, project.getId(), parseResult.getTemplates());
        if (parseResult.getRequirements() != null && !parseResult.getRequirements().isEmpty()) {
            bidDocumentVectorService.indexRequirements(tenantId, project.getId(), parseResult.getRequirements());
        }

        log.info("招标文件自动索引完成，内容长度: {}, 模板数: {}, projectId: {}",
            parseResult.getContent() != null ? parseResult.getContent().length() : 0,
            parseResult.getTemplates().size(), project.getId());

    } catch (Exception e) {
        log.warn("招标文件自动索引失败，将使用降级策略继续生成: {}", e.getMessage());
    }
}

/**
 * 构建章节内容 SSE 消息（单章节）
 */
private String buildChapterSseMessage(String type, Long chapterId, String message, int progress) {
    JSONObject json = new JSONObject();
    json.set("type", type);
    json.set("chapterId", chapterId);
    json.set("message", message);
    json.set("progress", progress);
    return json.toString();
}

/**
 * 构建批量生成 SSE 消息
 */
private String buildBatchSseMessage(String type, String message, int total, int current, int progress, Long chapterId) {
    JSONObject json = new JSONObject();
    json.set("type", type);
    json.set("message", message);
    json.set("total", total);
    json.set("current", current);
    json.set("progress", progress);
    if (chapterId != null) {
        json.set("chapterId", chapterId);
    }
    return json.toString();
}

@Override
public void updateChapterType(Long id, String chapterType) {
    if (!"template".equals(chapterType) && !"generate".equals(chapterType)) {
        throw new ServiceException("章节类型非法，仅支持 template/generate");
    }
    BizSubmissionChapter chapter = baseMapper.selectById(id);
    if (chapter == null) {
        return;
    }
    chapter.setChapterType(chapterType);
    baseMapper.updateById(chapter);
}

@Override
public Boolean deleteById(Long id) {
    return baseMapper.deleteById(id) > 0;
}

@Override
public void addChapter(Long submissionDocumentId, Long parentId, String chapterTitle, String chapterType, String reasonDescription) {
    BizSubmissionChapter chapter = new BizSubmissionChapter();
    chapter.setSubmissionDocumentId(submissionDocumentId);
    chapter.setParentId(parentId);
    chapter.setChapterTitle(chapterTitle);
    chapter.setChapterType(chapterType);
    chapter.setReasonDescription(reasonDescription);
    chapter.setGenerationStatus("pending");
    chapter.setGenerationProgress(0);
    chapter.setSortOrder(0);
    chapter.setChapterLevel(1);
    baseMapper.insert(chapter);
}

@Override
public void generateChapterStructure(Long submissionId, Long documentConfigId) {
    // 获取当前用户ID和租户ID，传入异步方法（异步线程无安全上下文）
    Long userId = LoginHelper.getUserId();
    String tenantId = TenantHelper.getTenantId();
    // 异步执行生成任务
    SpringUtils.getBean(IBizSubmissionChapterService.class)
        .doGenerateChapterStructure(submissionId, documentConfigId, userId, tenantId);
}

@Override
@Async
@Transactional(rollbackFor = Exception.class)
public void doGenerateChapterStructure(Long submissionId, Long documentConfigId, Long userId, String tenantId) {
    // 在异步线程中设置租户上下文，确保数据写入正确的租户
    TenantHelper.setDynamic(tenantId);
    try {
        log.info("开始生成章节结构，投标项目ID: {}, 文档配置ID: {}", submissionId, documentConfigId);

        // 标记为生成中（进度0），刷新页面可感知状态
        updateSubmissionProgress(submissionId, 0);

        // 推送开始消息
        SseMessageUtils.sendMessage(userId, buildSseMessage("start", "开始生成章节结构", 0, null));

        // 1. 获取投标项目信息
        log.info("查询投标项目，submissionId: {}", submissionId);
        updateSubmissionProgress(submissionId, 10);
        SseMessageUtils.sendMessage(userId, buildSseMessage("progress", "正在加载项目信息", 10, null));

        BizBidSubmission submission = submissionMapper.selectById(submissionId);
        log.info("查询结果: {}", submission);
        if (submission == null) {
            updateSubmissionProgress(submissionId, 0);
            SseMessageUtils.sendMessage(userId, buildSseMessage("error", "投标项目不存在", 0, null));
            return;
        }

        // 2. 获取文档配置
        BizDocumentConfig documentConfig = documentConfigMapper.selectById(documentConfigId);
        if (documentConfig == null) {
            updateSubmissionProgress(submissionId, 0);
            SseMessageUtils.sendMessage(userId, buildSseMessage("error", "文档配置不存在", 0, null));
            return;
        }

        // 3. 获取招标项目信息
        updateSubmissionProgress(submissionId, 20);
        SseMessageUtils.sendMessage(userId, buildSseMessage("progress", "正在加载招标项目信息", 20, null));
        BizBidProject project = projectMapper.selectById(submission.getBidProjectId());
        if (project == null) {
            updateSubmissionProgress(submissionId, 0);
            SseMessageUtils.sendMessage(userId, buildSseMessage("error", "招标项目不存在", 0, null));
            return;
        }

        // 4. 获取招标文件附件
        updateSubmissionProgress(submissionId, 30);
        SseMessageUtils.sendMessage(userId, buildSseMessage("progress", "正在加载招标文件", 30, null));
        if (StrUtil.isBlank(project.getAttachments())) {
            updateSubmissionProgress(submissionId, 0);
            SseMessageUtils.sendMessage(userId, buildSseMessage("error", "招标项目没有上传招标文件", 0, null));
            return;
        }

        // 从 attachments 字段获取附件ID（可能是逗号分隔的多个ID）
        String[] attachmentIds = project.getAttachments().split(",");
        if (attachmentIds.length == 0) {
            updateSubmissionProgress(submissionId, 0);
            SseMessageUtils.sendMessage(userId, buildSseMessage("error", "招标项目没有上传招标文件", 0, null));
            return;
        }

        // 获取第一个附件的文件URL
        Long ossId = Long.parseLong(attachmentIds[0].trim());
        SysOss sysOss = sysOssMapper.selectById(ossId);
        if (sysOss == null) {
            updateSubmissionProgress(submissionId, 0);
            SseMessageUtils.sendMessage(userId, buildSseMessage("error", "招标文件不存在", 0, null));
            return;
        }

        String fileUrl = sysOss.getUrl();
        if (StrUtil.isBlank(fileUrl)) {
            updateSubmissionProgress(submissionId, 0);
            SseMessageUtils.sendMessage(userId, buildSseMessage("error", "招标文件URL为空", 0, null));
            return;
        }

        // 5. 构建 AI 提示词
        updateSubmissionProgress(submissionId, 40);
        SseMessageUtils.sendMessage(userId, buildSseMessage("progress", "正在调用AI生成章节结构", 40, null));
        String prompt = buildChapterGenerationPrompt(project, documentConfig);

        // 6. 调用 qwen-long 生成章节结构
        String aiResponse;
        try {
            aiResponse = aiChatService.chatWithDocumentUrl(fileUrl, prompt);
            updateSubmissionProgress(submissionId, 70);
            SseMessageUtils.sendMessage(userId, buildSseMessage("progress", "AI生成完成，正在解析结果", 70, null));
        } catch (Exception e) {
            log.error("AI 生成章节结构失败", e);
            updateSubmissionProgress(submissionId, 0);
            SseMessageUtils.sendMessage(userId, buildSseMessage("error", "AI 生成章节结构失败: " + e.getMessage(), 0, null));
            return;
        }

        // 7. 解析 AI 返回的 JSON 并插入数据库（递归插入，自动处理父子关系）
        updateSubmissionProgress(submissionId, 80);
        SseMessageUtils.sendMessage(userId, buildSseMessage("progress", "正在保存章节结构", 80, null));
        parseChapterJson(aiResponse, submissionId, documentConfigId);

        log.info("章节结构保存完成，准备发送成功消息");

        // 8. 在事务提交后发送成功消息；若当前无事务同步上下文，立即发送兜底
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.info("事务已提交，发送成功消息");
                    markChapterStructureGeneratedSuccess(submissionId, documentConfigId, userId);
                }
            });
        } else {
            log.warn("未检测到事务同步上下文，立即发送章节结构生成成功消息");
            markChapterStructureGeneratedSuccess(submissionId, documentConfigId, userId);
        }

    } catch (Exception e) {
        log.error("生成章节结构异常", e);
        updateSubmissionProgress(submissionId, 0);
        SseMessageUtils.sendMessage(userId, buildSseMessage("error", "生成章节结构异常: " + e.getMessage(), 0, null));
    } finally {
        TenantHelper.clearDynamic();
    }
}

/**
 * 更新投标项目的章节生成进度
 * @param submissionId 投标项目ID
 * @param progress 进度(0-100)
 */
private void updateSubmissionProgress(Long submissionId, int progress) {
    try {
        BizBidSubmission update = new BizBidSubmission();
        update.setId(submissionId);
        update.setGenerationProgress(progress);
        submissionMapper.updateById(update);
    } catch (Exception e) {
        log.warn("更新生成进度失败: {}", e.getMessage());
    }
}

/**
 * 章节结构生成成功后的统一收尾
 * - 更新标书配置状态为 structure_generated（已生成目录）
 * - 重新计算投标项目整体进度
 * - 发送 SSE success，供前端自动刷新目录
 */
private void markChapterStructureGeneratedSuccess(Long submissionId, Long documentConfigId, Long userId) {
    try {
        // 更新标书配置状态为"已生成目录"
        BizDocumentConfig configUpdate = new BizDocumentConfig();
        configUpdate.setId(documentConfigId);
        configUpdate.setGenerationStatus("structure_generated");
        documentConfigMapper.updateById(configUpdate);

        // 重新计算投标项目整体进度
        recalculateSubmissionProgress(submissionId);
    } catch (Exception e) {
        log.warn("更新章节结构生成成功状态失败: {}", e.getMessage());
    }
    SseMessageUtils.sendMessage(userId, buildSseMessage("success", "章节结构生成完成", 100, null));
}

/**
 * 根据各标书配置状态重新计算投标项目整体进度
 * pending=0, structure_generated=33, content_generated=66, exported=100
 */
private void recalculateSubmissionProgress(Long submissionId) {
    try {
        List<BizDocumentConfig> configs = documentConfigMapper.selectList(
            Wrappers.lambdaQuery(BizDocumentConfig.class)
                .eq(BizDocumentConfig::getBidSubmissionId, submissionId)
                .eq(BizDocumentConfig::getStatus, "active")
        );
        if (configs.isEmpty()) return;
        int totalScore = 0;
        for (BizDocumentConfig config : configs) {
            String s = config.getGenerationStatus();
            totalScore += switch (s != null ? s : "") {
                case "structure_generated" -> 33;
                case "content_generated"  -> 66;
                case "exported"           -> 100;
                default                   -> 0;
            };
        }
        int avgProgress = totalScore / configs.size();
        BizBidSubmission progressUpdate = new BizBidSubmission();
        progressUpdate.setId(submissionId);
        progressUpdate.setGenerationProgress(avgProgress);
        submissionMapper.updateById(progressUpdate);
    } catch (Exception e) {
        log.warn("重新计算投标项目进度失败: {}", e.getMessage());
    }
}

/**
 * 构建SSE消息
 */
private String buildSseMessage(String type, String message, int progress, String data) {
    JSONObject json = new JSONObject();
    json.set("type", type);
    json.set("message", message);
    json.set("progress", progress);
    json.set("data", data);
    return json.toString();
}

@Override
public void regenerateChapterStructure(Long submissionId, Long documentConfigId) {
    log.info("重新生成章节结构，投标项目ID: {}, 文档配置ID: {}", submissionId, documentConfigId);

    // 1. 删除已有的章节
    LambdaQueryWrapper<BizSubmissionChapter> wrapper = Wrappers.lambdaQuery();
    wrapper.eq(BizSubmissionChapter::getBidSubmissionId, submissionId);
    wrapper.eq(BizSubmissionChapter::getSubmissionDocumentId, documentConfigId);
    baseMapper.delete(wrapper);

    // 2. 重新生成
    generateChapterStructure(submissionId, documentConfigId);
}

/**
 * 批量更新章节排序
 */
@Override
@Transactional(rollbackFor = Exception.class)
public void updateChapterSort(List<Map<String, Object>> sortItems) {
    for (Map<String, Object> item : sortItems) {
        // 支持 Number 和 String 类型的数字转换
        Long id = parseToLong(item.get("id"));
        Long parentId = parseToLong(item.get("parentId"));
        Integer sortOrder = parseToInteger(item.get("sortOrder"));
        Integer chapterLevel = parseToInteger(item.get("chapterLevel"));
        String chapterNo = item.get("chapterNo") instanceof String s ? s : null;
        if (id == null) continue;

        // 使用 LambdaUpdateWrapper 显式设置字段，绕过实体 updateStrategy
        var wrapper = Wrappers.lambdaUpdate(BizSubmissionChapter.class)
            .eq(BizSubmissionChapter::getId, id);
        if (parentId != null) wrapper.set(BizSubmissionChapter::getParentId, parentId);
        if (sortOrder != null) wrapper.set(BizSubmissionChapter::getSortOrder, sortOrder);
        if (chapterLevel != null) wrapper.set(BizSubmissionChapter::getChapterLevel, chapterLevel);
        if (chapterNo != null) wrapper.set(BizSubmissionChapter::getChapterNo, chapterNo);
        baseMapper.update(null, wrapper);
    }
}

/**
 * 将对象转换为 Long，支持 Number 和 String 类型
 */
private Long parseToLong(Object value) {
    if (value instanceof Number n) {
        return n.longValue();
    } else if (value instanceof String s && !s.isEmpty()) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    return null;
}

/**
 * 将对象转换为 Integer，支持 Number 和 String 类型
 */
private Integer parseToInteger(Object value) {
    if (value instanceof Number n) {
        return n.intValue();
    } else if (value instanceof String s && !s.isEmpty()) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    return null;
}

@Override
@Transactional(rollbackFor = Exception.class)
public void clearChapters(Long submissionId, Long documentId) {
    LambdaQueryWrapper<BizSubmissionChapter> lqw = Wrappers.lambdaQuery();
    if (submissionId != null) {
        lqw.eq(BizSubmissionChapter::getBidSubmissionId, submissionId);
    }
    if (documentId != null) {
        lqw.eq(BizSubmissionChapter::getSubmissionDocumentId, documentId);
    }
    baseMapper.delete(lqw);

    // 清空章节后，重置对应标书配置状态为 pending，并重算整体进度
    if (documentId != null) {
        try {
            BizDocumentConfig configUpdate = new BizDocumentConfig();
            configUpdate.setId(documentId);
            configUpdate.setGenerationStatus("pending");
            documentConfigMapper.updateById(configUpdate);
            if (submissionId != null) {
                recalculateSubmissionProgress(submissionId);
            }
        } catch (Exception e) {
            log.warn("重置标书配置状态(pending)失败: {}", e.getMessage());
        }
    }
}

/**
 */
private String buildChapterGenerationPrompt(BizBidProject project, BizDocumentConfig documentConfig) {
    String documentTypeDesc = switch (documentConfig.getDocumentType()) {
        case "technical" -> "技术标";
        case "commercial" -> "商务标";
        case "complete" -> "完整标书";
        default -> "标书";
    };

    return String.format("""
            基于以下招标文件，生成%s的章节目录结构。

            项目信息：
            - 项目名称：%s
            - 招标单位：%s
            - 项目类型：%s
            - 预算金额：%s

            要求：
            1. 输出严格的 JSON 格式
            2. 章节层级不超过 4 层
            3. 每个父章节（level 1-2）必须包含 reasonDescription 字段，说明为什么需要这个章节（50-100字）
            4. 章节编号格式：
               - 一级章节使用中文：第一章、第二章、第三章...
               - 二级及以下使用数字：1.1、1.2、1.1.1、1.1.1.1
            5. 章节标题简洁明确
            6. 总输出控制在 7000 token 以内
            7. 3级及以下章节的 reasonDescription 可以为空字符串
            8. 每个章节必须包含 chapterType 字段，取值为 "template" 或 "generate"：
               - "template"：招标文件中已提供规定格式/固定模板的章节（例如：格式一、格式二、
                 附表、投标函格式、法定代表人授权委托书格式、开标一览表、报价表格式、
                 资格审查表、投标保证金格式等带有固定表格或固定文本的章节）
               - "generate"：需要投标人自行编写的章节（例如：技术方案、实施计划、
                 项目理解、人员配置、售后服务方案等需要根据项目情况撰写的章节）
            9. 判断 chapterType 的关键依据：如果招标文件中该章节包含"格式"、"模板"、
               "范本"、"样式"、"附表"、"按以下格式"、"参照以下格式"等字样，
               或者包含需要填写的固定表格/固定文本框架，则标记为 "template"

            JSON Schema（必须严格遵守）：
            {
              "chapters": [
                {
                  "chapterNo": "第一章",
                  "chapterTitle": "投标函及投标函附录",
                  "chapterLevel": 1,
                  "chapterType": "template",
                  "reasonDescription": "生成说明（1-2级必填，3级以下可为空）",
                  "children": [
                    {
                      "chapterNo": "1.1",
                      "chapterTitle": "投标函",
                      "chapterLevel": 2,
                      "chapterType": "template",
                      "reasonDescription": "生成说明",
                      "children": []
                    }
                  ]
                },
                {
                  "chapterNo": "第二章",
                  "chapterTitle": "技术方案",
                  "chapterLevel": 1,
                  "chapterType": "generate",
                  "reasonDescription": "生成说明",
                  "children": [
                    {
                      "chapterNo": "2.1",
                      "chapterTitle": "项目理解与需求分析",
                      "chapterLevel": 2,
                      "chapterType": "generate",
                      "reasonDescription": "生成说明",
                      "children": []
                    }
                  ]
                }
              ]
            }

            请直接返回 JSON，不要有任何其他文字说明。
            """,
            documentTypeDesc,
            project.getProjectName(),
            project.getBidOrg(),
            project.getProjectType(),
            project.getBudgetAmount()
        );
    }

    /**
     * 解析 AI 返回的 JSON 为章节列表
     */
    private void parseChapterJson(String jsonResponse, Long submissionId, Long documentConfigId) {
        try {
            // 提取 JSON 部分（去除可能的 markdown 代码块标记）
            String cleanJson = jsonResponse.trim();
            if (cleanJson.startsWith("```json")) {
                cleanJson = cleanJson.substring(7);
            }
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.substring(3);
            }
            if (cleanJson.endsWith("```")) {
                cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
            }
            cleanJson = cleanJson.trim();

            // 尝试解析，如果失败则尝试修复截断的 JSON
            JSONObject root;
            try {
                root = JSONUtil.parseObj(cleanJson);
            } catch (Exception parseEx) {
                log.warn("JSON 解析失败，尝试修复截断的 JSON...");
                cleanJson = repairTruncatedJson(cleanJson);
                root = JSONUtil.parseObj(cleanJson);
            }
            JSONArray chapters = root.getJSONArray("chapters");

            int sortOrder = 0;
            for (Object obj : chapters) {
                JSONObject chapterJson = (JSONObject) obj;
                sortOrder++;
                parseAndInsertChapterRecursive(chapterJson, submissionId, documentConfigId, 0L, sortOrder);
            }

        } catch (Exception e) {
            log.error("解析章节 JSON 失败: {}", jsonResponse, e);
            throw new ServiceException("解析 AI 返回的章节结构失败: " + e.getMessage());
        }
    }

    /**
     * 修复被截断的 JSON 字符串
     * AI 输出超过 token 限制时会被截断，导致 JSON 不完整
     * 策略：找到最后一个完整的章节对象，截断后面不完整的部分，补齐括号
     */
    private String repairTruncatedJson(String json) {
        // 找到最后一个完整的 } 后跟 , 或 ] 的位置（表示一个完整的章节对象结束）
        // 从后往前找最后一个 "chapterNo" 或 "chapterTitle" 出现的位置
        int lastCompleteObj = -1;
        int braceDepth = 0;

        // 从后向前扫描，找到最后一个平衡的 } 位置
        for (int i = json.length() - 1; i >= 0; i--) {
            char c = json.charAt(i);
            if (c == '}') {
                braceDepth++;
            } else if (c == '{') {
                braceDepth--;
                if (braceDepth == 0) {
                    // 检查这个 {} 块是否包含 chapterTitle（说明是一个完整的章节对象）
                    int endBrace = findMatchingBrace(json, i);
                    if (endBrace > i) {
                        String block = json.substring(i, endBrace + 1);
                        if (block.contains("chapterTitle") && block.contains("chapterNo")) {
                            lastCompleteObj = endBrace;
                            break;
                        }
                    }
                }
            }
        }

        if (lastCompleteObj <= 0) {
            // 无法找到完整对象，返回原始 JSON 让上层报错
            return json;
        }

        // 截断到最后一个完整对象之后
        String truncated = json.substring(0, lastCompleteObj + 1);

        // 补齐未闭合的括号
        int openBrackets = 0, closeBrackets = 0;
        int openBraces = 0, closeBraces = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < truncated.length(); i++) {
            char c = truncated.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '[') openBrackets++;
            else if (c == ']') closeBrackets++;
            else if (c == '{') openBraces++;
            else if (c == '}') closeBraces++;
        }

        StringBuilder sb = new StringBuilder(truncated);
        for (int i = 0; i < openBrackets - closeBrackets; i++) sb.append(']');
        for (int i = 0; i < openBraces - closeBraces; i++) sb.append('}');

        log.info("JSON 修复完成，原始长度: {}，修复后长度: {}", json.length(), sb.length());
        return sb.toString();
    }

    /**
     * 找到从 startIndex 开始的 { 对应的 } 位置
     */
    private int findMatchingBrace(String json, int startIndex) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = startIndex; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /**
     * 递归解析并插入章节（先插入父节点获取ID，再插入子节点）
     */
    private void parseAndInsertChapterRecursive(JSONObject chapterJson, Long submissionId, Long documentConfigId,
                                                Long parentId, int sortOrder) {
        BizSubmissionChapter chapter = new BizSubmissionChapter();
        chapter.setBidSubmissionId(submissionId);
        chapter.setSubmissionDocumentId(documentConfigId);
        chapter.setParentId(parentId);
        chapter.setChapterNo(chapterJson.getStr("chapterNo"));
        chapter.setChapterTitle(chapterJson.getStr("chapterTitle"));
        chapter.setChapterLevel(chapterJson.getInt("chapterLevel"));
        chapter.setReasonDescription(chapterJson.getStr("reasonDescription", ""));
        chapter.setSortOrder(sortOrder);
        String chapterType = chapterJson.getStr("chapterType", "generate");
        if (!"template".equals(chapterType) && !"generate".equals(chapterType)) {
            chapterType = "generate";
        }
        chapter.setChapterType(chapterType);
        chapter.setGenerationStatus("pending");
        chapter.setGenerationProgress(0);
        chapter.setAiModel("qwen-long-latest");

        // 插入当前章节，获取生成的 ID
        baseMapper.insert(chapter);
        Long currentChapterId = chapter.getId();

        // 递归处理子章节
        JSONArray children = chapterJson.getJSONArray("children");
        if (children != null && !children.isEmpty()) {
            int childSortOrder = 0;
            for (Object childObj : children) {
                JSONObject childJson = (JSONObject) childObj;
                childSortOrder++;
                // 使用当前章节的 ID 作为子章节的 parentId
                parseAndInsertChapterRecursive(childJson, submissionId, documentConfigId, currentChapterId, childSortOrder);
            }
        }
    }

    /**
     * 递归解析章节（已废弃，使用 parseAndInsertChapterRecursive 代替）
     */
    @Deprecated
    private void parseChapterRecursive(JSONObject chapterJson, Long submissionId, Long documentConfigId,
                                       Long parentId, int sortOrder, List<BizSubmissionChapter> result) {
        BizSubmissionChapter chapter = new BizSubmissionChapter();
        chapter.setBidSubmissionId(submissionId);
        chapter.setSubmissionDocumentId(documentConfigId);
        chapter.setParentId(parentId);
        chapter.setChapterNo(chapterJson.getStr("chapterNo"));
        chapter.setChapterTitle(chapterJson.getStr("chapterTitle"));
        chapter.setChapterLevel(chapterJson.getInt("chapterLevel"));
        chapter.setReasonDescription(chapterJson.getStr("reasonDescription", ""));
        chapter.setSortOrder(sortOrder);
        String chapterType = chapterJson.getStr("chapterType", "generate");
        if (!"template".equals(chapterType) && !"generate".equals(chapterType)) {
            chapterType = "generate";
        }
        chapter.setChapterType(chapterType);
        chapter.setGenerationStatus("pending");
        chapter.setGenerationProgress(0);
        chapter.setAiModel("qwen-long-latest");

        result.add(chapter);

        // 递归处理子章节
        JSONArray children = chapterJson.getJSONArray("children");
        if (children != null && !children.isEmpty()) {
            int childSortOrder = 0;
            for (Object childObj : children) {
                JSONObject childJson = (JSONObject) childObj;
                childSortOrder++;
                parseChapterRecursive(childJson, submissionId, documentConfigId, 0L, childSortOrder, result);
            }
        }
    }

}

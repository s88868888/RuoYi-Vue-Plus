package org.dromara.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.resource.domain.*;
import org.dromara.resource.mapper.*;
import org.dromara.resource.service.IBidDocumentGenerationService;
import org.dromara.resource.service.agent.*;
import org.dromara.resource.service.agent.ChapterDataEnricher.AttachmentRef;
import org.dromara.resource.service.agent.ChapterDataEnricher.ChapterContentType;
import org.dromara.resource.service.agent.ChapterDataEnricher.EnrichmentResult;
import org.dromara.resource.domain.vo.BidSubmissionProgressVo;
import org.dromara.system.domain.SysOss;
import org.dromara.system.mapper.SysOssMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 标书文档生成Service实现（完整版）
 *
 * 生成流程：
 * 1. 解析招标文件（提取要求、模板）
 * 2. 生成章节结构
 * 3. 处理模板章节（填充公司信息）
 * 4. AI生成章节
 * 5. 合成最终文档
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class BidDocumentGenerationServiceImplV2 implements IBidDocumentGenerationService {

    // Mappers
    private final BizBidSubmissionMapper submissionMapper;
    private final BizSubmissionDocumentMapper documentMapper;
    private final BizSubmissionChapterMapper chapterMapper;
    private final BizBidProjectMapper bidProjectMapper;
    private final SysOssMapper sysOssMapper;
    private final BizSubmissionDocumentLogMapper logMapper;

    // Agents
    private final DocumentParserAgent documentParserAgent;
    private final ChapterStructureAgent chapterStructureAgent;
    private final CompanyInfoRetrievalAgent companyInfoRetrievalAgent;
    private final TemplateFillAgent templateFillAgent;
    private final ChapterGenerationAgent chapterGenerationAgent;
    private final DocumentAssemblyAgent documentAssemblyAgent;
    private final ImageRetrievalAgent imageRetrievalAgent;
    private final ChapterDataEnricher chapterDataEnricher;

    @Async("bidGenerationExecutor")
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateDocumentsAsync(Long submissionId) {
        log.info("========== 开始生成投标项目文档 ==========");
        log.info("投标项目ID: {}", submissionId);

        BizBidSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            log.error("投标项目不存在: {}", submissionId);
            return;
        }

        // Step 1: 更新顶层状态为 generating
        submission.setStatus("generating");
        submission.setGenerationProgress(0);
        submission.setStartTime(new Date());
        submission.setTotalDocuments(0);
        submission.setCompletedDocuments(0);
        submission.setFailedDocuments(0);
        submission.setErrorMessage(null);
        submissionMapper.updateById(submission);

        try {
            // ========== 步骤1: 解析招标文件 ==========
            logProgress(submissionId, null, "解析招标文件", 5, "开始解析招标文件");

            SysOss bidDocOss = getBidDocOss(submission.getBidProjectId());
            if (bidDocOss == null) {
                throw new RuntimeException("未找到招标文件附件，请先上传招标文件");
            }

            DocumentParserAgent.ParseResult parseResult = documentParserAgent.parse(
                bidDocOss.getUrl(),
                bidDocOss.getFileSuffix() != null ? bidDocOss.getFileSuffix().replace(".", "") : "",
                bidDocOss.getOriginalName()
            );

            // 保存解析结果（无需写回附件表，解析结果已在 ParseResult 中）
            logProgress(submissionId, null, "解析招标文件", 10,
                String.format("招标文件解析完成，提取到%d个模板", parseResult.getTemplates().size()));

            // ========== 步骤2: 生成章节结构 ==========
            logProgress(submissionId, null, "生成章节结构", 15, "开始生成章节结构");

            // 获取所有需要生成的文档
            List<BizSubmissionDocument> documents = documentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizSubmissionDocument>()
                    .eq(BizSubmissionDocument::getBidSubmissionId, submissionId)
            );

            int totalDocuments = documents.size();
            int completedDocuments = 0;

            // 为每个文档生成章节结构
            for (BizSubmissionDocument document : documents) {
                logProgress(submissionId, document.getId(), "生成章节结构",
                    15 + (completedDocuments * 5 / totalDocuments),
                    String.format("为文档[%s]生成章节结构", document.getDocumentName()));

                List<BizSubmissionChapter> chapters = chapterStructureAgent.generateStructure(
                    submissionId, document.getId(), parseResult
                );

                // 保存章节结构（需要处理父子关系）
                saveChapterStructure(chapters);

                logProgress(submissionId, document.getId(), "生成章节结构",
                    20 + (completedDocuments * 5 / totalDocuments),
                    String.format("文档[%s]章节结构生成完成，共%d个章节",
                        document.getDocumentName(), chapters.size()));

                completedDocuments++;
            }

            // Step 2: 章节结构生成完毕，更新文档总数和进度
            submission.setTotalDocuments(totalDocuments);
            submission.setGenerationProgress(20);
            submissionMapper.updateById(submission);

            // ========== 步骤3-4: 逐个处理章节 ==========
            completedDocuments = 0;

            for (BizSubmissionDocument document : documents) {
                try {
                    logProgress(submissionId, document.getId(), "生成文档内容",
                        25 + (completedDocuments * 60 / totalDocuments),
                        String.format("开始生成文档[%s]", document.getDocumentName()));

                    // Step 3: 标记文档为 generating
                    document.setGenerationStatus("generating");
                    document.setGenerationProgress(0);
                    document.setGenerationStartTime(new Date());
                    documentMapper.updateById(document);

                    // 获取该文档的所有章节
                    List<BizSubmissionChapter> chapters = chapterMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizSubmissionChapter>()
                            .eq(BizSubmissionChapter::getSubmissionDocumentId, document.getId())
                            .orderByAsc(BizSubmissionChapter::getSortOrder)
                    );

                    // 获取公司信息
                    Map<String, String> companyInfo = getCompanyInfo(document.getCompanyId(), parseResult);

                    // 构建生成上下文
                    ChapterGenerationAgent.GenerationContext context = buildGenerationContext(
                        submission, companyInfo, parseResult
                    );

                    // 处理每个章节
                    int totalChapters = chapters.size();
                    int processedChapters = 0;

                    for (BizSubmissionChapter chapter : chapters) {
                        try {
                            logProgress(submissionId, document.getId(),
                                "生成章节: " + chapter.getChapterTitle(),
                                25 + (completedDocuments * 60 / totalDocuments) +
                                    (processedChapters * 60 / totalDocuments / totalChapters),
                                String.format("正在生成章节[%s - %s]",
                                    chapter.getChapterNo(), chapter.getChapterTitle()));

                            chapter.setGenerationStatus("generating");
                            chapterMapper.updateById(chapter);

                            String content;
                            if ("template".equals(chapter.getChapterType())) {
                                // 模板章节：填充公司信息
                                content = processTemplateChapter(chapter, companyInfo, parseResult);
                            } else {
                                // AI生成章节：先进行数据富化
                                EnrichmentResult enrichment =
                                    chapterDataEnricher.enrich(chapter, document.getCompanyId());

                                if (enrichment.getContentType() != ChapterContentType.NONE) {
                                    context.setStructuredData(enrichment.getStructuredDataMarkdown());
                                    context.setFormatInstructions(enrichment.getFormatInstructions());
                                    context.setChapterContentType(enrichment.getContentType().name());

                                    logProgress(submissionId, document.getId(),
                                        "数据富化: " + chapter.getChapterTitle(),
                                        25 + (completedDocuments * 60 / totalDocuments) +
                                            (processedChapters * 60 / totalDocuments / totalChapters),
                                        String.format("检测为规定格式[%s]，注入%d条数据",
                                            enrichment.getContentType(), enrichment.getRecordCount()));
                                }

                                // AI生成（prompt 现在可能包含结构化数据）
                                content = chapterGenerationAgent.generateChapter(chapter, context);

                                // 自动追加附件图片
                                if (enrichment.getContentType() != ChapterContentType.NONE
                                    && enrichment.getAttachments() != null
                                    && !enrichment.getAttachments().isEmpty()) {
                                    content = appendAttachmentImages(content, enrichment.getAttachments());
                                }

                                // 清除 per-chapter 覆盖，避免影响下一章节
                                context.setStructuredData(null);
                                context.setFormatInstructions(null);
                                context.setChapterContentType(null);
                            }

                            // 解析并替换图片占位符为真实图片
                            content = imageRetrievalAgent.resolveImagePlaceholders(content, document.getCompanyId());

                            // 保存章节内容
                            chapter.setChapterContent(content);
                            chapter.setGenerationStatus("completed");
                            chapter.setGenerationProgress(100);
                            chapter.setGenerationEndTime(new Date());
                            chapterMapper.updateById(chapter);

                            processedChapters++;

                        } catch (Exception e) {
                            log.error("章节生成失败: {}", chapter.getChapterTitle(), e);
                            chapter.setGenerationStatus("failed");
                            chapter.setErrorMessage(e.getMessage());
                            chapterMapper.updateById(chapter);
                        }
                    }

                    // ========== 步骤5: 合成文档 ==========
                    logProgress(submissionId, document.getId(), "合成文档",
                        85 + (completedDocuments * 10 / totalDocuments),
                        String.format("合成文档[%s]", document.getDocumentName()));

                    String finalDocument = documentAssemblyAgent.assembleDocument(
                        chapters, submission.getProjectName()
                    );

                    // 保存最终文档
                    document.setDocumentContent(finalDocument);
                    document.setGenerationStatus("completed");
                    document.setGenerationProgress(100);
                    document.setGenerationEndTime(new Date());
                    documentMapper.updateById(document);

                    // Step 4: 递增 completedDocuments，更新顶层进度
                    completedDocuments++;
                    int overallProgress = 20 + (completedDocuments * 80 / totalDocuments);
                    submission.setCompletedDocuments(completedDocuments);
                    submission.setGenerationProgress(overallProgress);
                    submissionMapper.updateById(submission);

                } catch (Exception docEx) {
                    // Step 5: 文档级失败处理
                    log.error("文档生成失败: {}", document.getDocumentName(), docEx);
                    document.setGenerationStatus("failed");
                    document.setErrorMessage(docEx.getMessage());
                    documentMapper.updateById(document);

                    submission.setFailedDocuments(submission.getFailedDocuments() + 1);
                    submissionMapper.updateById(submission);
                    completedDocuments++;
                }
            }

            // ========== 完成 ==========
            submission.setStatus("generated");
            submission.setGenerationProgress(100);
            submission.setCompletedDocuments(totalDocuments);
            submission.setEndTime(new Date());
            submissionMapper.updateById(submission);

            logProgress(submissionId, null, "完成", 100,
                String.format("所有文档生成完成，共%d个文档", totalDocuments));

            log.info("========== 投标项目文档生成完成 ==========");

        } catch (Exception e) {
            log.error("投标项目生成失败", e);
            submission.setStatus("failed");
            submission.setErrorMessage(e.getMessage());
            submission.setEndTime(new Date());
            submissionMapper.updateById(submission);

            logProgress(submissionId, null, "失败", 0, "生成失败: " + e.getMessage());
        }
    }

    /**
     * 获取招标项目关联的第一个 OSS 文件（招标文件附件）
     */
    private SysOss getBidDocOss(Long bidProjectId) {
        BizBidProject project = bidProjectMapper.selectById(bidProjectId);
        if (project == null || project.getAttachments() == null || project.getAttachments().isBlank()) {
            return null;
        }
        String[] ossIdArr = project.getAttachments().split(",");
        for (String ossIdStr : ossIdArr) {
            try {
                Long ossId = Long.parseLong(ossIdStr.trim());
                SysOss oss = sysOssMapper.selectById(ossId);
                if (oss != null) {
                    return oss;
                }
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    /**
     * 保存章节结构（处理父子关系）
     */
    private void saveChapterStructure(List<BizSubmissionChapter> chapters) {
        // 先保存所有章节
        for (BizSubmissionChapter chapter : chapters) {
            chapterMapper.insert(chapter);
        }

        // TODO: 如果需要处理父子关系的ID引用，这里需要二次更新
    }

    /**
     * 获取公司信息
     */
    private Map<String, String> getCompanyInfo(
        Long companyId,
        DocumentParserAgent.ParseResult parseResult) {

        // 提取所有模板中的占位符
        Set<String> allPlaceholders = new HashSet<>();
        for (DocumentParserAgent.TemplateInfo template : parseResult.getTemplates()) {
            if (template.getPlaceholders() != null) {
                allPlaceholders.addAll(template.getPlaceholders());
            }
        }

        // 从Milvus检索公司信息
        return companyInfoRetrievalAgent.retrieveCompanyInfo(
            companyId,
            new ArrayList<>(allPlaceholders)
        );
    }

    /**
     * 处理模板章节
     */
    private String processTemplateChapter(
        BizSubmissionChapter chapter,
        Map<String, String> companyInfo,
        DocumentParserAgent.ParseResult parseResult) {

        // 从解析结果中找到对应的模板
        DocumentParserAgent.TemplateInfo template = parseResult.getTemplates().stream()
            .filter(t -> t.getChapterNo().equals(chapter.getChapterNo()))
            .findFirst()
            .orElse(null);

        if (template == null) {
            log.warn("未找到章节{}的模板", chapter.getChapterNo());
            return "";
        }

        // 填充模板
        return templateFillAgent.fillTemplate(template.getContent(), companyInfo);
    }

    /**
     * 构建生成上下文
     */
    private ChapterGenerationAgent.GenerationContext buildGenerationContext(
        BizBidSubmission submission,
        Map<String, String> companyInfo,
        DocumentParserAgent.ParseResult parseResult) {

        ChapterGenerationAgent.GenerationContext context =
            new ChapterGenerationAgent.GenerationContext();

        context.setProjectName(submission.getProjectName());
        context.setBidOrg(submission.getBidOrg());
        context.setProjectType(submission.getProjectType());
        context.setBudgetAmount(submission.getBudgetAmount().toString());
        context.setProjectDesc(submission.getProjectDesc());
        context.setCompanyInfo(companyInfo);
        context.setRequirements(parseResult.getRequirements());
        context.setScoringCriteria(parseResult.getScoringCriteria());

        return context;
    }

    /**
     * 在章节内容末尾追加附件证明材料图片
     *
     * @param content     原始章节内容
     * @param attachments 附件引用列表
     * @return 追加了附件图片的内容
     */
    private String appendAttachmentImages(String content, List<AttachmentRef> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return content;
        }

        // 上限 30 张，防止章节过长
        int limit = Math.min(attachments.size(), 30);
        List<AttachmentRef> limited = attachments.subList(0, limit);

        // 按类型分组
        Map<String, List<AttachmentRef>> grouped = limited.stream()
            .collect(Collectors.groupingBy(AttachmentRef::getType, LinkedHashMap::new, Collectors.toList()));

        StringBuilder sb = new StringBuilder(content);
        sb.append("\n\n---\n\n");
        sb.append("### 附件：证明材料\n\n");

        for (Map.Entry<String, List<AttachmentRef>> entry : grouped.entrySet()) {
            String typeLabel = switch (entry.getKey()) {
                case "QUALIFICATION" -> "资质证书";
                case "PERFORMANCE" -> "业绩证明";
                case "PERSONNEL" -> "人员证书";
                case "PATENT" -> "专利证书";
                case "FINANCE" -> "财务资料";
                case "PRODUCT" -> "产品/设备";
                case "COMPANY" -> "企业证照";
                default -> entry.getKey();
            };
            sb.append("#### ").append(typeLabel).append("\n\n");

            for (AttachmentRef ref : entry.getValue()) {
                sb.append(imageRetrievalAgent.buildImageHtml(ref.getImageUrl(), ref.getCaption()));
            }
            sb.append("\n");
        }

        if (attachments.size() > limit) {
            sb.append(String.format("<p style=\"text-align:center;color:#999;font-size:12px;\">"
                + "（共%d张附件，此处展示前%d张）</p>\n", attachments.size(), limit));
        }

        return sb.toString();
    }

    /**
     * 记录进度日志
     */
    private void logProgress(Long submissionId, Long documentId,
                            String stage, int progress, String message) {
        BizSubmissionDocumentLog log = new BizSubmissionDocumentLog();
        log.setBidSubmissionId(submissionId);
        log.setSubmissionDocumentId(documentId);
        log.setLogLevel("INFO");
        log.setStage(stage);
        log.setProgress(progress);
        log.setLogMessage(message);
        log.setLogTime(new Date());
        logMapper.insert(log);
    }

    @Override
    public void generateSingleDocument(Long documentId) {
        // TODO: 实现单个文档重新生成
    }

    @Override
    public BidSubmissionProgressVo getGenerationProgress(Long submissionId) {
        BizBidSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) return null;

        BidSubmissionProgressVo vo = new BidSubmissionProgressVo();
        vo.setSubmissionId(submissionId);
        vo.setStatus(submission.getStatus());
        vo.setOverallProgress(submission.getGenerationProgress());
        vo.setTotalDocuments(submission.getTotalDocuments());
        vo.setCompletedDocuments(submission.getCompletedDocuments());
        vo.setFailedDocuments(submission.getFailedDocuments());

        // 查询各文档状态
        List<BizSubmissionDocument> docs = documentMapper.selectList(
            new LambdaQueryWrapper<BizSubmissionDocument>()
                .eq(BizSubmissionDocument::getBidSubmissionId, submissionId)
        );
        List<BidSubmissionProgressVo.DocumentProgressItem> docItems = docs.stream().map(doc -> {
            BidSubmissionProgressVo.DocumentProgressItem item = new BidSubmissionProgressVo.DocumentProgressItem();
            item.setDocumentId(doc.getId());
            item.setCompanyName(doc.getCompanyName());
            item.setDocumentType(doc.getDocumentType());
            item.setGenerationStatus(doc.getGenerationStatus());
            item.setProgress(doc.getGenerationProgress());
            item.setErrorMessage(doc.getErrorMessage());
            return item;
        }).collect(Collectors.toList());
        vo.setDocuments(docItems);

        // 查询最近20条日志
        List<BizSubmissionDocumentLog> logs = logMapper.selectList(
            new LambdaQueryWrapper<BizSubmissionDocumentLog>()
                .eq(BizSubmissionDocumentLog::getBidSubmissionId, submissionId)
                .orderByDesc(BizSubmissionDocumentLog::getLogTime)
                .last("LIMIT 20")
        );
        List<BidSubmissionProgressVo.LogItem> logItems = logs.stream().map(l -> {
            BidSubmissionProgressVo.LogItem li = new BidSubmissionProgressVo.LogItem();
            li.setStage(l.getStage());
            li.setMessage(l.getLogMessage());
            li.setProgress(l.getProgress());
            li.setLevel(l.getLogLevel());
            li.setTime(l.getLogTime() != null ? l.getLogTime().toString() : null);
            return li;
        }).collect(Collectors.toList());
        vo.setLogs(logItems);

        return vo;
    }

    @Override
    public void cancelGeneration(Long submissionId) {
        // TODO: 实现取消生成
    }

}

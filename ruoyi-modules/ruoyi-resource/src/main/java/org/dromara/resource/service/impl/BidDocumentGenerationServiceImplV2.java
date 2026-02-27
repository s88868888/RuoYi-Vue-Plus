package org.dromara.resource.service.impl;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.resource.domain.*;
import org.dromara.resource.mapper.*;
import org.dromara.resource.service.IBidDocumentGenerationService;
import org.dromara.resource.service.agent.*;
import org.dromara.resource.domain.vo.BidSubmissionProgressVo;
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
    private final BizBidProjectAttachmentMapper attachmentMapper;
    private final BizSubmissionDocumentLogMapper logMapper;

    // Agents
    private final DocumentParserAgent documentParserAgent;
    private final ChapterStructureAgent chapterStructureAgent;
    private final CompanyInfoRetrievalAgent companyInfoRetrievalAgent;
    private final TemplateFillAgent templateFillAgent;
    private final ChapterGenerationAgent chapterGenerationAgent;
    private final DocumentAssemblyAgent documentAssemblyAgent;

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

        try {
            // ========== 步骤1: 解析招标文件 ==========
            logProgress(submissionId, null, "解析招标文件", 5, "开始解析招标文件");

            BizBidProjectAttachment attachment = getAttachment(submission.getBidProjectId());
            if (attachment == null) {
                throw new RuntimeException("未找到招标文件附件，请先上传招标文件");
            }

            DocumentParserAgent.ParseResult parseResult = documentParserAgent.parse(attachment);

            // 保存解析结果
            attachment.setParsedContent(parseResult.getContent());
            attachment.setParsedStructure(parseResult.getStructure());
            attachment.setExtractedTemplates(JSON.toJSONString(parseResult.getTemplates()));
            attachment.setParseStatus("completed");
            attachmentMapper.updateById(attachment);

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

            submission.setChapterStructureGenerated("1");
            submissionMapper.updateById(submission);

            // ========== 步骤3-4: 逐个处理章节 ==========
            completedDocuments = 0;

            for (BizSubmissionDocument document : documents) {
                logProgress(submissionId, document.getId(), "生成文档内容",
                    25 + (completedDocuments * 60 / totalDocuments),
                    String.format("开始生成文档[%s]", document.getDocumentName()));

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
                            // AI生成章节
                            content = chapterGenerationAgent.generateChapter(chapter, context);
                        }

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

                completedDocuments++;
            }

            // ========== 完成 ==========
            submission.setSubmissionStatus("completed");
            submission.setGenerationProgress(100);
            submission.setCompletedDocuments(totalDocuments);
            submission.setEndTime(new Date());
            submissionMapper.updateById(submission);

            logProgress(submissionId, null, "完成", 100,
                String.format("所有文档生成完成，共%d个文档", totalDocuments));

            log.info("========== 投标项目文档生成完成 ==========");

        } catch (Exception e) {
            log.error("投标项目生成失败", e);
            submission.setSubmissionStatus("failed");
            submission.setErrorMessage(e.getMessage());
            submission.setEndTime(new Date());
            submissionMapper.updateById(submission);

            logProgress(submissionId, null, "失败", 0, "生成失败: " + e.getMessage());
        }
    }

    /**
     * 获取招标文件附件
     */
    private BizBidProjectAttachment getAttachment(Long bidProjectId) {
        return attachmentMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BizBidProjectAttachment>()
                .eq(BizBidProjectAttachment::getBidProjectId, bidProjectId)
                .eq(BizBidProjectAttachment::getAttachmentType, "bid_doc")
                .orderByDesc(BizBidProjectAttachment::getCreateTime)
                .last("LIMIT 1")
        );
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
        // TODO: 实现进度查询（复用之前的代码）
        return null;
    }

    @Override
    public void cancelGeneration(Long submissionId) {
        // TODO: 实现取消生成
    }

}

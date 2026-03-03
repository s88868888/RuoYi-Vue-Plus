package org.dromara.resource.service.impl;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.domain.VectorSearchResult;
import org.dromara.common.ai.service.MilvusVectorStoreService;
import org.dromara.resource.domain.BizBidProjectAttachment;
import org.dromara.resource.domain.BizBidSubmission;
import org.dromara.resource.domain.BizDocumentConfig;
import org.dromara.resource.domain.BizSubmissionChapter;
import org.dromara.resource.domain.BizSubmissionDocument;
import org.dromara.resource.domain.BizSubmissionDocumentLog;
import org.dromara.resource.domain.vo.BidSubmissionProgressVo;
import org.dromara.resource.domain.vo.BizSubmissionDocumentVo;
import org.dromara.resource.mapper.BizBidProjectAttachmentMapper;
import org.dromara.resource.mapper.BizBidSubmissionMapper;
import org.dromara.resource.mapper.BizDocumentConfigMapper;
import org.dromara.resource.mapper.BizSubmissionChapterMapper;
import org.dromara.resource.mapper.BizSubmissionDocumentLogMapper;
import org.dromara.resource.mapper.BizSubmissionDocumentMapper;
import org.dromara.resource.service.IBidDocumentGenerationService;
import org.dromara.resource.service.SseProgressService;
import org.dromara.resource.service.agent.ChapterGenerationAgent;
import org.dromara.resource.service.agent.ChapterGenerationAgent.GenerationContext;
import org.dromara.resource.service.agent.ChapterStructureAgent;
import org.dromara.resource.service.agent.CompanyInfoRetrievalAgent;
import org.dromara.resource.service.agent.DocumentParserAgent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 标书文档生成Service业务层处理
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BidDocumentGenerationServiceImpl implements IBidDocumentGenerationService {

    private final BizBidSubmissionMapper submissionMapper;
    private final BizSubmissionDocumentMapper documentMapper;
    private final BizSubmissionDocumentLogMapper documentLogMapper;
    private final BizDocumentConfigMapper documentConfigMapper;
    private final BizBidProjectAttachmentMapper attachmentMapper;
    private final BizSubmissionChapterMapper chapterMapper;

    private final DocumentParserAgent documentParserAgent;
    private final ChapterStructureAgent chapterStructureAgent;
    private final ChapterGenerationAgent chapterGenerationAgent;
    private final CompanyInfoRetrievalAgent companyInfoRetrievalAgent;
    private final MilvusVectorStoreService milvusVectorStoreService;
    private final SseProgressService sseProgressService;

    @Async("bidGenerationExecutor")
    @Override
    public void generateDocumentsAsync(Long submissionId) {
        log.info("开始异步生成投标项目文档，submissionId: {}", submissionId);

        BizBidSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            log.error("投标项目不存在: {}", submissionId);
            return;
        }

        try {
            // 1. 查询配置表中的配置
            LambdaQueryWrapper<BizDocumentConfig> configWrapper = Wrappers.lambdaQuery();
            configWrapper.eq(BizDocumentConfig::getBidSubmissionId, submissionId);
            configWrapper.eq(BizDocumentConfig::getStatus, "active");
            configWrapper.orderByAsc(BizDocumentConfig::getCompanyId, BizDocumentConfig::getDocumentType, BizDocumentConfig::getDocumentNo);
            List<BizDocumentConfig> configs = documentConfigMapper.selectList(configWrapper);

            if (configs.isEmpty()) {
                log.warn("投标项目没有配置文档: {}", submissionId);
                submission.setSubmissionStatus("failed");
                submission.setErrorMessage("没有配置文档");
                submissionMapper.updateById(submission);
                return;
            }

            // 2. 创建文档记录
            List<BizSubmissionDocument> documents = createDocumentRecords(submission, configs);

            // 3. 更新总文档数
            submission.setTotalDocuments(documents.size());
            submissionMapper.updateById(submission);

            // 4. 记录日志
            logProgress(submissionId, null, "初始化", 0, "文档记录创建完成，共" + documents.size() + "个文档");

            // 5. 获取招标文件附件
            LambdaQueryWrapper<BizBidProjectAttachment> attachmentWrapper = Wrappers.lambdaQuery();
            attachmentWrapper.eq(BizBidProjectAttachment::getBidProjectId, submission.getBidProjectId());
            attachmentWrapper.eq(BizBidProjectAttachment::getAttachmentType, "bid_doc");
            attachmentWrapper.last("LIMIT 1");
            BizBidProjectAttachment attachment = attachmentMapper.selectOne(attachmentWrapper);

            if (attachment == null) {
                throw new RuntimeException("未找到招标文件附件，请先上传招标文件");
            }

            // 6. 解析招标文件
            logProgress(submissionId, null, "解析", 5, "开始解析招标文件: " + attachment.getAttachmentName());
            DocumentParserAgent.ParseResult parseResult = documentParserAgent.parse(attachment);
            logProgress(submissionId, null, "解析", 10, "招标文件解析完成");

            submission.setGenerationProgress(10);
            submissionMapper.updateById(submission);

            // 7. 遍历每个文档，生成章节结构和内容
            int completedDocs = 0;
            for (int i = 0; i < configs.size(); i++) {
                BizDocumentConfig config = configs.get(i);
                BizSubmissionDocument doc = documents.get(i);

                try {
                    generateDocumentContent(submission, config, doc, parseResult);
                    completedDocs++;
                    submission.setCompletedDocuments(completedDocs);
                } catch (Exception e) {
                    log.error("文档生成失败，documentId: {}", doc.getId(), e);
                    doc.setGenerationStatus("failed");
                    doc.setErrorMessage(e.getMessage());
                    documentMapper.updateById(doc);
                    submission.setFailedDocuments(
                        submission.getFailedDocuments() != null ? submission.getFailedDocuments() + 1 : 1
                    );
                }
                submissionMapper.updateById(submission);
            }

            // 8. 完成
            submission.setSubmissionStatus("completed");
            submission.setGenerationProgress(100);
            submission.setEndTime(new Date());
            submission.setCompletedDocuments(completedDocs);
            submissionMapper.updateById(submission);

            logProgress(submissionId, null, "完成", 100, "所有文档生成完成");
            sseProgressService.complete(submissionId);

        } catch (Exception e) {
            log.error("投标项目生成失败: {}", submissionId, e);
            submission.setSubmissionStatus("failed");
            submission.setErrorMessage(e.getMessage());
            submission.setEndTime(new Date());
            submissionMapper.updateById(submission);
            logProgress(submissionId, null, "失败", 0, "生成失败: " + e.getMessage());
            sseProgressService.fail(submissionId, e.getMessage());
        }
    }

    /**
     * 生成单个文档的章节结构及内容
     */
    private void generateDocumentContent(
        BizBidSubmission submission,
        BizDocumentConfig config,
        BizSubmissionDocument doc,
        DocumentParserAgent.ParseResult parseResult) {

        Long submissionId = submission.getId();
        Long docId = doc.getId();

        // 标记文档为生成中
        doc.setGenerationStatus("generating");
        documentMapper.updateById(doc);

        // 生成章节结构（AI 分析招标文件）
        logProgress(submissionId, docId, "目录生成", 15, "开始生成章节结构: " + doc.getDocumentName());
        List<ChapterStructureAgent.ChapterNode> chapterNodes =
            chapterStructureAgent.generateStructureNodes(submissionId, docId, parseResult);

        // 递归保存章节，确保 parent_id 正确
        List<BizSubmissionChapter> savedChapters = new ArrayList<>();
        saveChaptersRecursive(chapterNodes, submissionId, docId, 0L, savedChapters);

        logProgress(submissionId, docId, "目录生成", 20,
            "章节结构生成完成，共" + savedChapters.size() + "个章节");

        // 更新文档总章节数（记录在 config 上）
        List<BizSubmissionChapter> leafChapters = getLeafChapters(savedChapters);
        int total = leafChapters.size();

        if (total == 0) {
            log.warn("文档无叶子章节，跳过内容生成，docId: {}", docId);
            doc.setGenerationStatus("completed");
            doc.setGenerationProgress(100);
            documentMapper.updateById(doc);
            return;
        }

        // 遍历叶子章节，逐一生成内容
        int done = 0;
        for (BizSubmissionChapter chapter : leafChapters) {
            try {
                chapter.setGenerationStatus("generating");
                chapter.setGenerationStartTime(new Date());
                chapterMapper.updateById(chapter);

                if ("template".equals(chapter.getChapterType())) {
                    // 模板章节：从 Milvus 检索公司信息填充占位符
                    fillTemplateChapter(chapter, config.getCompanyId());
                } else {
                    // AI 生成章节：每章节单独检索 Milvus 知识库
                    generateAiChapter(chapter, submission, config, parseResult);
                }

                Date now = new Date();
                chapter.setGenerationStatus("completed");
                chapter.setGenerationProgress(100);
                chapter.setGenerationEndTime(now);
                if (chapter.getGenerationStartTime() != null) {
                    long duration = (now.getTime() - chapter.getGenerationStartTime().getTime()) / 1000;
                    chapter.setGenerationDuration((int) duration);
                }
                chapterMapper.updateById(chapter);

            } catch (Exception e) {
                log.error("生成章节失败: {} - {}", chapter.getChapterNo(), chapter.getChapterTitle(), e);
                chapter.setGenerationStatus("failed");
                chapter.setErrorMessage(e.getMessage());
                chapterMapper.updateById(chapter);
            }

            done++;
            int progress = 20 + (int) (done * 75.0 / total);
            doc.setGenerationProgress(progress);
            documentMapper.updateById(doc);

            submission.setGenerationProgress(progress);
            submissionMapper.updateById(submission);

            // 推送 SSE 进度
            sseProgressService.push(submissionId, buildProgressEvent(submission, doc, chapter));

            logProgress(submissionId, docId, "章节生成", progress,
                "章节生成完成: " + chapter.getChapterNo() + " " + chapter.getChapterTitle()
                    + " [" + done + "/" + total + "]");
        }

        // 文档标记为完成
        doc.setGenerationStatus("completed");
        doc.setGenerationProgress(100);
        documentMapper.updateById(doc);
    }

    /**
     * 填充模板章节（从 Milvus 检索公司信息替换占位符）
     */
    private void fillTemplateChapter(BizSubmissionChapter chapter, Long companyId) {
        String templateSource = chapter.getTemplateSource();
        if (templateSource == null || templateSource.isBlank()) {
            chapter.setChapterContent("");
            return;
        }

        String placeholdersJson = chapter.getTemplatePlaceholders();
        if (placeholdersJson == null || placeholdersJson.isBlank()) {
            chapter.setChapterContent(templateSource);
            return;
        }

        try {
            List<String> placeholders = JSON.parseArray(placeholdersJson, String.class);
            if (placeholders.isEmpty()) {
                chapter.setChapterContent(templateSource);
                return;
            }

            Map<String, String> values = companyInfoRetrievalAgent.retrieveCompanyInfo(companyId, placeholders);
            String content = templateSource;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isBlank()) {
                    content = content.replace(entry.getKey(), entry.getValue());
                }
            }
            chapter.setChapterContent(content);
        } catch (Exception e) {
            log.warn("填充模板章节失败，使用原始模板: {}", e.getMessage());
            chapter.setChapterContent(templateSource);
        }
    }

    /**
     * 使用 AI + Milvus RAG 生成章节内容
     */
    private void generateAiChapter(
        BizSubmissionChapter chapter,
        BizBidSubmission submission,
        BizDocumentConfig config,
        DocumentParserAgent.ParseResult parseResult) {

        // 从 Milvus 检索与当前章节最相关的知识
        List<VectorSearchResult> knowledge = Collections.emptyList();
        try {
            knowledge = milvusVectorStoreService.search(
                "company_" + config.getCompanyId(),
                chapter.getChapterTitle(),
                null,
                config.getCompanyId(),
                null,
                5
            );
        } catch (Exception e) {
            log.warn("Milvus 知识检索失败，将不注入知识库内容继续生成: {}", e.getMessage());
        }

        // 构建生成上下文
        GenerationContext ctx = buildContext(submission, config, parseResult);
        ctx.setRelevantKnowledge(knowledge);

        String content = chapterGenerationAgent.generateChapter(chapter, ctx);
        chapter.setChapterContent(content);
        chapter.setAiModel("qwen3.5-plus");
    }

    /**
     * 递归保存章节节点，先保存父节点获取数据库ID，再保存子节点
     */
    private void saveChaptersRecursive(
        List<ChapterStructureAgent.ChapterNode> nodes,
        Long submissionId,
        Long documentId,
        Long parentId,
        List<BizSubmissionChapter> savedList) {

        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        int sortOrder = 0;
        for (ChapterStructureAgent.ChapterNode node : nodes) {
            BizSubmissionChapter chapter = new BizSubmissionChapter();
            chapter.setBidSubmissionId(submissionId);
            chapter.setSubmissionDocumentId(documentId);
            chapter.setParentId(parentId);
            chapter.setChapterNo(node.getChapterNo());
            chapter.setChapterTitle(node.getTitle());
            chapter.setChapterLevel(node.getLevel());
            chapter.setChapterType(node.getType() != null ? node.getType() : "generate");
            chapter.setSortOrder(sortOrder++);
            chapter.setGenerationStatus("pending");
            chapter.setGenerationProgress(0);

            // 先插入父节点获取真实 ID
            chapterMapper.insert(chapter);
            savedList.add(chapter);

            // 再递归处理子节点（使用刚保存的 ID 作为 parentId）
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                saveChaptersRecursive(node.getChildren(), submissionId, documentId, chapter.getId(), savedList);
            }
        }
    }

    /**
     * 从已保存的章节列表中提取叶子章节（无子章节的节点）
     */
    private List<BizSubmissionChapter> getLeafChapters(List<BizSubmissionChapter> allChapters) {
        // 收集所有有子章节的 parentId
        java.util.Set<Long> parentIds = allChapters.stream()
            .map(BizSubmissionChapter::getParentId)
            .filter(pid -> pid != null && pid > 0)
            .collect(Collectors.toSet());

        // 叶子章节 = 其 ID 不在任何章节的 parentId 中
        return allChapters.stream()
            .filter(ch -> !parentIds.contains(ch.getId()))
            .collect(Collectors.toList());
    }

    /**
     * 构建生成上下文
     */
    private GenerationContext buildContext(
        BizBidSubmission submission,
        BizDocumentConfig config,
        DocumentParserAgent.ParseResult parseResult) {

        GenerationContext ctx = new GenerationContext();
        ctx.setProjectName(submission.getProjectName());
        ctx.setBidOrg(submission.getBidOrg());
        ctx.setProjectType(submission.getProjectType());
        ctx.setBudgetAmount(submission.getBudgetAmount() != null
            ? submission.getBudgetAmount().toPlainString() : "未知");
        ctx.setProjectDesc(submission.getProjectDesc());
        ctx.setRequirements(parseResult.getRequirements());
        ctx.setScoringCriteria(parseResult.getScoringCriteria());

        // 公司基础信息（先用一个默认 Map，模板章节会通过 Milvus 补充详细内容）
        Map<String, String> companyInfo = new LinkedHashMap<>();
        companyInfo.put("company_name", config.getCompanyName());
        ctx.setCompanyInfo(companyInfo);

        return ctx;
    }

    /**
     * 构建 SSE 进度事件数据
     */
    private Map<String, Object> buildProgressEvent(
        BizBidSubmission submission,
        BizSubmissionDocument doc,
        BizSubmissionChapter chapter) {

        Map<String, Object> event = new HashMap<>();
        event.put("submissionId", submission.getId());
        event.put("submissionStatus", submission.getSubmissionStatus());
        event.put("overallProgress", submission.getGenerationProgress());
        event.put("documentId", doc.getId());
        event.put("documentName", doc.getDocumentName());
        event.put("documentProgress", doc.getGenerationProgress());
        event.put("documentStatus", doc.getGenerationStatus());
        event.put("chapterId", chapter.getId());
        event.put("chapterTitle", chapter.getChapterTitle());
        event.put("chapterStatus", chapter.getGenerationStatus());
        return event;
    }

    // ─────────────────────────────────────────────────────────────────
    // 以下为原有方法（保留）
    // ─────────────────────────────────────────────────────────────────

    /**
     * 创建文档记录（基于配置表）
     */
    private List<BizSubmissionDocument> createDocumentRecords(BizBidSubmission submission, List<BizDocumentConfig> configs) {
        List<BizSubmissionDocument> documents = new ArrayList<>();

        for (BizDocumentConfig config : configs) {
            BizSubmissionDocument doc = createDocument(submission, config);
            documentMapper.insert(doc);
            documents.add(doc);
        }

        return documents;
    }

    /**
     * 创建单个文档记录（基于配置表）
     */
    private BizSubmissionDocument createDocument(BizBidSubmission submission, BizDocumentConfig config) {
        BizSubmissionDocument doc = new BizSubmissionDocument();
        doc.setBidSubmissionId(submission.getId());
        doc.setDocumentConfigId(config.getId());
        doc.setCompanyId(config.getCompanyId());
        doc.setCompanyName(config.getCompanyName());

        String typeName = switch (config.getDocumentType()) {
            case "commercial" -> "商务标";
            case "technical" -> "技术标";
            case "complete" -> "整本标书";
            default -> "标书";
        };

        doc.setDocumentName(String.format("%s-%s-%s%d", submission.getProjectName(), config.getCompanyName(), typeName, config.getDocumentNo()));
        doc.setDocumentType(config.getDocumentType());
        doc.setDocumentNo(config.getDocumentNo());
        doc.setGenerationStatus("pending");
        doc.setGenerationProgress(0);
        doc.setVersion(1);
        doc.setIsLatest("1");

        return doc;
    }

    @Override
    public void generateSingleDocument(Long documentId) {
        log.info("生成单个文档，documentId: {}", documentId);
        // TODO: 后续实现AI生成单个文档的逻辑
    }

    @Override
    public BidSubmissionProgressVo getGenerationProgress(Long submissionId) {
        BizBidSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new RuntimeException("投标项目不存在");
        }

        BidSubmissionProgressVo progressVo = new BidSubmissionProgressVo();
        progressVo.setSubmissionId(submissionId);
        progressVo.setSubmissionStatus(submission.getSubmissionStatus());
        progressVo.setOverallProgress(submission.getGenerationProgress());
        progressVo.setTotalDocuments(submission.getTotalDocuments());
        progressVo.setCompletedDocuments(submission.getCompletedDocuments());
        progressVo.setFailedDocuments(submission.getFailedDocuments());

        // 查询文档列表
        LambdaQueryWrapper<BizSubmissionDocument> docWrapper = Wrappers.lambdaQuery();
        docWrapper.eq(BizSubmissionDocument::getBidSubmissionId, submissionId);
        List<BizSubmissionDocumentVo> documents = documentMapper.selectVoList(docWrapper);

        List<BidSubmissionProgressVo.DocumentProgressItem> docItems = documents.stream().map(doc -> {
            BidSubmissionProgressVo.DocumentProgressItem item = new BidSubmissionProgressVo.DocumentProgressItem();
            item.setDocumentId(doc.getId());
            item.setCompanyName(doc.getCompanyName());
            item.setDocumentType(doc.getDocumentType());
            item.setDocumentTypeName(getDocumentTypeName(doc.getDocumentType()));
            item.setDocumentNo(doc.getDocumentNo());
            item.setGenerationStatus(doc.getGenerationStatus());
            item.setStatusText(getStatusText(doc.getGenerationStatus()));
            item.setProgress(doc.getGenerationProgress());
            item.setErrorMessage(doc.getErrorMessage());
            return item;
        }).collect(Collectors.toList());

        progressVo.setDocuments(docItems);

        // 查询日志
        LambdaQueryWrapper<BizSubmissionDocumentLog> logWrapper = Wrappers.lambdaQuery();
        logWrapper.eq(BizSubmissionDocumentLog::getBidSubmissionId, submissionId);
        logWrapper.orderByDesc(BizSubmissionDocumentLog::getLogTime);
        logWrapper.last("LIMIT 50");
        List<BizSubmissionDocumentLog> logs = documentLogMapper.selectList(logWrapper);

        List<BidSubmissionProgressVo.LogItem> logItems = logs.stream().map(log -> {
            BidSubmissionProgressVo.LogItem item = new BidSubmissionProgressVo.LogItem();
            item.setTime(DateUtil.formatDateTime(log.getLogTime()));
            item.setLevel(log.getLogLevel());
            item.setMessage(log.getLogMessage());
            item.setStage(log.getStage());
            item.setProgress(log.getProgress());
            return item;
        }).collect(Collectors.toList());

        progressVo.setLogs(logItems);

        return progressVo;
    }

    @Override
    public void cancelGeneration(Long submissionId) {
        log.info("取消生成任务，submissionId: {}", submissionId);
        // TODO: 后续实现取消任务的逻辑
        logProgress(submissionId, null, "取消", 0, "用户取消生成任务");
    }

    /**
     * 记录进度日志
     */
    private void logProgress(Long submissionId, Long documentId, String stage, int progress, String message) {
        BizSubmissionDocumentLog log = new BizSubmissionDocumentLog();
        log.setBidSubmissionId(submissionId);
        log.setSubmissionDocumentId(documentId);
        log.setLogLevel("INFO");
        log.setStage(stage);
        log.setProgress(progress);
        log.setLogMessage(message);
        log.setLogTime(new Date());
        documentLogMapper.insert(log);
    }

    /**
     * 获取文档类型名称
     */
    private String getDocumentTypeName(String docType) {
        return switch (docType) {
            case "commercial" -> "商务标";
            case "technical" -> "技术标";
            case "complete" -> "整本标书";
            default -> "未知";
        };
    }

    /**
     * 获取状态文本
     */
    private String getStatusText(String status) {
        return switch (status) {
            case "pending" -> "待生成";
            case "generating" -> "生成中";
            case "completed" -> "已完成";
            case "failed" -> "失败";
            default -> "未知";
        };
    }

}

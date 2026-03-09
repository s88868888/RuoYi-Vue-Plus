package org.dromara.resource.service.impl;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.domain.VectorSearchResult;
import org.dromara.common.ai.service.MilvusVectorStoreService;
import org.dromara.resource.domain.BizBidProject;
import org.dromara.resource.domain.BizBidSubmission;
import org.dromara.resource.domain.BizDocumentConfig;
import org.dromara.resource.domain.BizSubmissionChapter;
import org.dromara.resource.domain.BizSubmissionDocument;
import org.dromara.resource.domain.BizSubmissionDocumentLog;
import org.dromara.resource.domain.vo.BidSubmissionProgressVo;
import org.dromara.resource.domain.vo.BizSubmissionDocumentVo;
import org.dromara.resource.mapper.BizBidProjectMapper;
import org.dromara.resource.mapper.BizBidSubmissionMapper;
import org.dromara.resource.mapper.BizDocumentConfigMapper;
import org.dromara.resource.mapper.BizSubmissionChapterMapper;
import org.dromara.resource.mapper.BizSubmissionDocumentLogMapper;
import org.dromara.resource.mapper.BizSubmissionDocumentMapper;
import org.dromara.resource.service.BidDocumentVectorService;
import org.dromara.resource.service.IBidDocumentGenerationService;
import org.dromara.resource.service.SseProgressService;
import org.dromara.resource.service.agent.ChapterGenerationAgent;
import org.dromara.resource.service.agent.ChapterGenerationAgent.GenerationContext;
import org.dromara.resource.service.agent.ChapterStructureAgent;
import org.dromara.resource.service.agent.CompanyInfoRetrievalAgent;
import org.dromara.resource.service.agent.DocumentParserAgent;
import org.dromara.system.domain.SysOss;
import org.dromara.system.mapper.SysOssMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 标书文档生成Service业务层处理
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Slf4j
@Service
public class BidDocumentGenerationServiceImpl implements IBidDocumentGenerationService {

    private final BizBidSubmissionMapper submissionMapper;
    private final BizSubmissionDocumentMapper documentMapper;
    private final BizSubmissionDocumentLogMapper documentLogMapper;
    private final BizDocumentConfigMapper documentConfigMapper;
    private final BizBidProjectMapper bidProjectMapper;
    private final SysOssMapper sysOssMapper;
    private final BizSubmissionChapterMapper chapterMapper;

    private final DocumentParserAgent documentParserAgent;
    private final ChapterStructureAgent chapterStructureAgent;
    private final ChapterGenerationAgent chapterGenerationAgent;
    private final CompanyInfoRetrievalAgent companyInfoRetrievalAgent;
    private final MilvusVectorStoreService milvusVectorStoreService;
    private final BidDocumentVectorService bidDocumentVectorService;
    private final SseProgressService sseProgressService;
    private final Executor bidGenerationExecutor;

    public BidDocumentGenerationServiceImpl(
        BizBidSubmissionMapper submissionMapper,
        BizSubmissionDocumentMapper documentMapper,
        BizSubmissionDocumentLogMapper documentLogMapper,
        BizDocumentConfigMapper documentConfigMapper,
        BizBidProjectMapper bidProjectMapper,
        SysOssMapper sysOssMapper,
        BizSubmissionChapterMapper chapterMapper,
        DocumentParserAgent documentParserAgent,
        ChapterStructureAgent chapterStructureAgent,
        ChapterGenerationAgent chapterGenerationAgent,
        CompanyInfoRetrievalAgent companyInfoRetrievalAgent,
        MilvusVectorStoreService milvusVectorStoreService,
        BidDocumentVectorService bidDocumentVectorService,
        SseProgressService sseProgressService,
        @Qualifier("bidGenerationExecutor") Executor bidGenerationExecutor) {
        this.submissionMapper = submissionMapper;
        this.documentMapper = documentMapper;
        this.documentLogMapper = documentLogMapper;
        this.documentConfigMapper = documentConfigMapper;
        this.bidProjectMapper = bidProjectMapper;
        this.sysOssMapper = sysOssMapper;
        this.chapterMapper = chapterMapper;
        this.documentParserAgent = documentParserAgent;
        this.chapterStructureAgent = chapterStructureAgent;
        this.chapterGenerationAgent = chapterGenerationAgent;
        this.companyInfoRetrievalAgent = companyInfoRetrievalAgent;
        this.milvusVectorStoreService = milvusVectorStoreService;
        this.bidDocumentVectorService = bidDocumentVectorService;
        this.sseProgressService = sseProgressService;
        this.bidGenerationExecutor = bidGenerationExecutor;
    }

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
                submission.setStatus("failed");
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

            // 5. 获取招标文件 OSS 信息
            SysOss bidDocOss = getBidDocOss(submission.getBidProjectId());

            if (bidDocOss == null) {
                throw new RuntimeException("未找到招标文件附件，请先上传招标文件");
            }

            // 6. 解析招标文件
            logProgress(submissionId, null, "解析", 5, "开始解析招标文件: " + bidDocOss.getOriginalName());
            String fileFormat = bidDocOss.getFileSuffix() != null ? bidDocOss.getFileSuffix().replace(".", "") : "";
            DocumentParserAgent.ParseResult parseResult = documentParserAgent.parse(
                bidDocOss.getUrl(), fileFormat, bidDocOss.getOriginalName());
            logProgress(submissionId, null, "解析", 10, "招标文件解析完成");

            // 6.5 全量存入Milvus（幂等：先清空再写入）
            String tenantId = submission.getTenantId();
            Long bidProjectId = submission.getBidProjectId();
            try {
                bidDocumentVectorService.clearProjectData(tenantId, bidProjectId);
                bidDocumentVectorService.indexDocumentContent(tenantId, bidProjectId, parseResult.getContent());
                bidDocumentVectorService.indexRequirements(tenantId, bidProjectId, parseResult.getRequirements());
                bidDocumentVectorService.indexStructure(tenantId, bidProjectId, parseResult.getStructure());
                bidDocumentVectorService.indexTemplates(tenantId, bidProjectId, parseResult.getTemplates());
                if (parseResult.getScoringCriteria() != null && !parseResult.getScoringCriteria().isEmpty()) {
                    bidDocumentVectorService.indexScoringCriteria(tenantId, bidProjectId,
                        JSON.toJSONString(parseResult.getScoringCriteria()));
                }
                logProgress(submissionId, null, "索引", 12, "招标文件已入Milvus向量库");
            } catch (Exception e) {
                log.warn("招标文件入Milvus失败，不影响生成流程: {}", e.getMessage());
            }

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
            submission.setStatus("generated");
            submission.setGenerationProgress(100);
            submission.setEndTime(new Date());
            submission.setCompletedDocuments(completedDocs);
            submissionMapper.updateById(submission);

            logProgress(submissionId, null, "完成", 100, "所有文档生成完成");
            sseProgressService.complete(submissionId);

        } catch (Exception e) {
            log.error("投标项目生成失败: {}", submissionId, e);
            submission.setStatus("failed");
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

        // 生成章节结构（AI 分析招标文件，按文档类型区分目录）
        logProgress(submissionId, docId, "目录生成", 15, "开始生成章节结构: " + doc.getDocumentName());
        List<ChapterStructureAgent.ChapterNode> chapterNodes =
            chapterStructureAgent.generateStructureNodes(submissionId, docId, parseResult, config.getDocumentType());

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

        // 并行生成叶子章节内容（Semaphore控制并发数=3）
        Semaphore semaphore = new Semaphore(3);
        AtomicInteger doneCount = new AtomicInteger(0);
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

        List<CompletableFuture<Void>> futures = leafChapters.stream()
            .map(chapter -> CompletableFuture.runAsync(() -> {
                try {
                    semaphore.acquire();

                    chapter.setGenerationStatus("generating");
                    chapter.setGenerationStartTime(new Date());
                    chapterMapper.updateById(chapter);

                    if ("template".equals(chapter.getChapterType())) {
                        fillTemplateChapter(chapter, submission, config.getCompanyId());
                    } else {
                        generateAiChapter(chapter, submission, config);
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

                    int current = doneCount.incrementAndGet();
                    int progress = 20 + (int) (current * 75.0 / total);

                    synchronized (doc) {
                        doc.setGenerationProgress(progress);
                        documentMapper.updateById(doc);
                    }
                    synchronized (submission) {
                        submission.setGenerationProgress(progress);
                        submissionMapper.updateById(submission);
                    }

                    sseProgressService.push(submissionId, buildProgressEvent(submission, doc, chapter));
                    logProgress(submissionId, docId, "章节生成", progress,
                        "章节生成完成: " + chapter.getChapterNo() + " " + chapter.getChapterTitle()
                            + " [" + current + "/" + total + "]");

                } catch (Exception e) {
                    log.error("生成章节失败: {} - {}", chapter.getChapterNo(), chapter.getChapterTitle(), e);
                    chapter.setGenerationStatus("failed");
                    chapter.setErrorMessage(e.getMessage());
                    chapterMapper.updateById(chapter);
                    errors.add(e);
                    doneCount.incrementAndGet();
                } finally {
                    semaphore.release();
                }
            }, bidGenerationExecutor))
            .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        if (!errors.isEmpty()) {
            log.warn("文档生成中有 {} 个章节失败", errors.size());
        }

        // 文档标记为完成
        doc.setGenerationStatus("completed");
        doc.setGenerationProgress(100);
        documentMapper.updateById(doc);
    }

    /**
     * 填充模板章节（支持项目信息和公司信息占位符）
     */
    private void fillTemplateChapter(BizSubmissionChapter chapter, BizBidSubmission submission, Long companyId) {
        String templateSource = chapter.getTemplateSource();
        if (templateSource == null || templateSource.isBlank()) {
            chapter.setChapterContent("");
            return;
        }

        try {
            String content = templateSource;

            // 1. 填充项目信息占位符（括号格式）
            BizBidProject project = bidProjectMapper.selectById(submission.getBidProjectId());
            if (project != null) {
                content = content.replace("(项目名称)", project.getProjectName() != null ? project.getProjectName() : "");
                content = content.replace("(招标单位)", project.getBidOrg() != null ? project.getBidOrg() : "");
                content = content.replace("(项目预算)", project.getBudgetAmount() != null ? project.getBudgetAmount().toString() : "");
                content = content.replace("(项目地区)", project.getProjectRegion() != null ? project.getProjectRegion() : "");
            }

            // 2. 填充公司信息占位符（如果有）
            String placeholdersJson = chapter.getTemplatePlaceholders();
            if (placeholdersJson != null && !placeholdersJson.isBlank()) {
                List<String> placeholders = JSON.parseArray(placeholdersJson, String.class);
                if (!placeholders.isEmpty()) {
                    Map<String, String> values = companyInfoRetrievalAgent.retrieveCompanyInfo(companyId, placeholders);
                    for (Map.Entry<String, String> entry : values.entrySet()) {
                        if (entry.getValue() != null && !entry.getValue().isBlank()) {
                            content = content.replace(entry.getKey(), entry.getValue());
                        }
                    }
                }
            }

            chapter.setChapterContent(content);
        } catch (Exception e) {
            log.warn("填充模板章节失败，使用原始模板: {}", e.getMessage());
            chapter.setChapterContent(templateSource);
        }
    }

    /**
     * 使用双重RAG生成章节内容（招标文件Milvus + 公司知识Milvus）
     */
    private void generateAiChapter(
        BizSubmissionChapter chapter,
        BizBidSubmission submission,
        BizDocumentConfig config) {

        String tenantId = submission.getTenantId();

        // RAG 1: 招标文件知识（从Milvus检索）
        List<VectorSearchResult> bidDocKnowledge = Collections.emptyList();
        try {
            bidDocKnowledge = bidDocumentVectorService.searchAll(
                tenantId, submission.getBidProjectId(), chapter.getChapterTitle(), 8);
        } catch (Exception e) {
            log.warn("Milvus 招标文件知识检索失败: {}", e.getMessage());
        }

        // RAG 2: 公司知识库（已有）
        List<VectorSearchResult> companyKnowledge = Collections.emptyList();
        try {
            companyKnowledge = milvusVectorStoreService.search(
                "company_" + config.getCompanyId(),
                chapter.getChapterTitle(),
                null,
                config.getCompanyId(),
                null,
                5
            );
        } catch (Exception e) {
            log.warn("Milvus 公司知识检索失败: {}", e.getMessage());
        }

        // 构建生成上下文（从Milvus结果构建，不依赖ParseResult）
        GenerationContext ctx = buildContextFromMilvus(submission, config, bidDocKnowledge);
        ctx.setRelevantKnowledge(companyKnowledge);
        ctx.setBidDocKnowledge(bidDocKnowledge);

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
     * 从Milvus检索结果构建生成上下文（双重RAG模式）
     */
    private GenerationContext buildContextFromMilvus(
        BizBidSubmission submission,
        BizDocumentConfig config,
        List<VectorSearchResult> bidDocKnowledge) {

        GenerationContext ctx = new GenerationContext();
        ctx.setProjectName(submission.getProjectName());
        ctx.setBidOrg(submission.getBidOrg());
        ctx.setProjectType(submission.getProjectType());
        ctx.setBudgetAmount(submission.getBudgetAmount() != null
            ? submission.getBudgetAmount().toPlainString() : "未知");
        ctx.setProjectDesc(submission.getProjectDesc());
        ctx.setDocumentType(config.getDocumentType());

        // 从Milvus结果中提取requirements
        List<String> requirements = bidDocKnowledge.stream()
            .filter(r -> "requirement".equals(r.getDocType()))
            .map(VectorSearchResult::getContent)
            .toList();
        ctx.setRequirements(requirements);

        // 从Milvus结果中提取scoringCriteria
        Map<String, String> scoring = new LinkedHashMap<>();
        bidDocKnowledge.stream()
            .filter(r -> "scoring".equals(r.getDocType()))
            .forEach(r -> scoring.put(r.getContent(), ""));
        ctx.setScoringCriteria(scoring);

        Map<String, String> companyInfo = new LinkedHashMap<>();
        companyInfo.put("company_name", config.getCompanyName());
        ctx.setCompanyInfo(companyInfo);

        return ctx;
    }

    /**
     * 构建生成上下文
     * @deprecated 使用 {@link #buildContextFromMilvus} 替代，基于Milvus检索结果构建
     */
    @Deprecated
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
        event.put("status", submission.getStatus());
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
        progressVo.setStatus(submission.getStatus());
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
        logProgress(submissionId, null, "取消", 0, "用户取消生成任务");
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

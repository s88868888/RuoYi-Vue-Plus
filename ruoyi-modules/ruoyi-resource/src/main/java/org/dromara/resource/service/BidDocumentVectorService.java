package org.dromara.resource.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.domain.VectorDocument;
import org.dromara.common.ai.domain.VectorSearchResult;
import org.dromara.common.ai.service.MilvusVectorStoreService;
import org.dromara.resource.service.agent.DocumentParserAgent;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 招标文件向量服务
 * 管理招标项目的Milvus Collection（创建/清空/删除）
 * 将不同类型的数据存入Milvus（chunk_type区分）
 * 提供按chunk_type的检索方法
 *
 * @author ruoyi
 * @date 2026-03-04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BidDocumentVectorService {

    private final MilvusVectorStoreService vectorStoreService;

    /** 默认分块大小（字符数） */
    private static final int DEFAULT_CHUNK_SIZE = 800;
    /** 默认重叠大小（字符数） */
    private static final int DEFAULT_OVERLAP = 100;

    /**
     * Collection命名: bid_doc_{tenantId}_{bidProjectId}
     */
    public String getCollectionName(String tenantId, Long bidProjectId) {
        return "bid_doc_" + tenantId + "_" + bidProjectId;
    }

    /**
     * 清空项目的所有向量数据（重新分析时调用）
     */
    public void clearProjectData(String tenantId, Long bidProjectId) {
        String collectionName = getCollectionName(tenantId, bidProjectId);
        vectorStoreService.dropCollection(collectionName);
        log.info("已清空项目向量数据: tenantId={}, bidProjectId={}", tenantId, bidProjectId);
    }

    /**
     * 清空项目指定chunk_type的数据（部分重新分析时调用）
     */
    public void clearProjectDataByType(String tenantId, Long bidProjectId, String chunkType) {
        String collectionName = getCollectionName(tenantId, bidProjectId);
        vectorStoreService.deleteByDocType(collectionName, tenantId, chunkType);
        log.info("已清空项目指定类型向量数据: tenantId={}, bidProjectId={}, chunkType={}", tenantId, bidProjectId, chunkType);
    }

    /**
     * 存入招标文件全文分块（chunk_type=content）
     */
    public void indexDocumentContent(String tenantId, Long bidProjectId, String fullText) {
        if (fullText == null || fullText.isBlank()) {
            return;
        }
        String collectionName = getCollectionName(tenantId, bidProjectId);
        List<String> chunks = smartChunk(fullText, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
        List<VectorDocument> docs = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            docs.add(buildDocument(tenantId, "content", chunks.get(i),
                Map.of("chunk_index", i, "total_chunks", chunks.size())));
        }
        vectorStoreService.insertDocuments(collectionName, docs);
        log.info("招标文件全文已入Milvus: {} 块, collection={}", chunks.size(), collectionName);
    }

    /**
     * 存入AI分析结果（chunk_type=ai_analysis，分段存入）
     */
    public void indexAiAnalysis(String tenantId, Long bidProjectId, String analysisResult) {
        if (analysisResult == null || analysisResult.isBlank()) {
            return;
        }
        String collectionName = getCollectionName(tenantId, bidProjectId);
        List<String> chunks = smartChunk(analysisResult, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
        List<VectorDocument> docs = new ArrayList<>();
        for (String chunk : chunks) {
            docs.add(buildDocument(tenantId, "ai_analysis", chunk, Map.of()));
        }
        vectorStoreService.insertDocuments(collectionName, docs);
        log.info("AI分析结果已入Milvus: {} 块, collection={}", chunks.size(), collectionName);
    }

    /**
     * 存入评分标准（chunk_type=scoring）
     */
    public void indexScoringCriteria(String tenantId, Long bidProjectId, String scoringCriteria) {
        if (scoringCriteria == null || scoringCriteria.isBlank()) {
            return;
        }
        String collectionName = getCollectionName(tenantId, bidProjectId);
        List<String> chunks = smartChunk(scoringCriteria, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
        List<VectorDocument> docs = new ArrayList<>();
        for (String chunk : chunks) {
            docs.add(buildDocument(tenantId, "scoring", chunk, Map.of()));
        }
        vectorStoreService.insertDocuments(collectionName, docs);
        log.info("评分标准已入Milvus: {} 块, collection={}", chunks.size(), collectionName);
    }

    /**
     * 存入契合度分析（chunk_type=match_analysis）
     */
    public void indexMatchAnalysis(String tenantId, Long bidProjectId, String matchAnalysis) {
        if (matchAnalysis == null || matchAnalysis.isBlank()) {
            return;
        }
        String collectionName = getCollectionName(tenantId, bidProjectId);
        List<String> chunks = smartChunk(matchAnalysis, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
        List<VectorDocument> docs = new ArrayList<>();
        for (String chunk : chunks) {
            docs.add(buildDocument(tenantId, "match_analysis", chunk, Map.of()));
        }
        vectorStoreService.insertDocuments(collectionName, docs);
        log.info("契合度分析已入Milvus: {} 块, collection={}", chunks.size(), collectionName);
    }

    /**
     * 存入招标要求列表（chunk_type=requirement）
     */
    public void indexRequirements(String tenantId, Long bidProjectId, List<String> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return;
        }
        String collectionName = getCollectionName(tenantId, bidProjectId);
        List<VectorDocument> docs = new ArrayList<>();
        for (int i = 0; i < requirements.size(); i++) {
            String req = requirements.get(i);
            if (req != null && !req.isBlank()) {
                docs.add(buildDocument(tenantId, "requirement", req,
                    Map.of("requirement_index", i)));
            }
        }
        if (!docs.isEmpty()) {
            vectorStoreService.insertDocuments(collectionName, docs);
            log.info("招标要求已入Milvus: {} 条, collection={}", docs.size(), collectionName);
        }
    }

    /**
     * 存入文档结构（chunk_type=structure）
     */
    public void indexStructure(String tenantId, Long bidProjectId, String structureJson) {
        if (structureJson == null || structureJson.isBlank()) {
            return;
        }
        String collectionName = getCollectionName(tenantId, bidProjectId);
        List<VectorDocument> docs = List.of(
            buildDocument(tenantId, "structure", structureJson, Map.of()));
        vectorStoreService.insertDocuments(collectionName, docs);
        log.info("文档结构已入Milvus: collection={}", collectionName);
    }

    /**
     * 存入模板章节（chunk_type=template）
     */
    public void indexTemplates(String tenantId, Long bidProjectId, List<DocumentParserAgent.TemplateInfo> templates) {
        if (templates == null || templates.isEmpty()) {
            return;
        }
        String collectionName = getCollectionName(tenantId, bidProjectId);
        List<VectorDocument> docs = new ArrayList<>();
        for (DocumentParserAgent.TemplateInfo template : templates) {
            String content = "模板章节: " + template.getTitle() + "\n" + template.getContent();
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("chapter_no", template.getChapterNo());
            metadata.put("title", template.getTitle());
            docs.add(buildDocument(tenantId, "template", content, metadata));
        }
        vectorStoreService.insertDocuments(collectionName, docs);
        log.info("模板章节已入Milvus: {} 个, collection={}", docs.size(), collectionName);
    }

    /**
     * 检索（按章节标题，可选chunk_type过滤）
     */
    public List<VectorSearchResult> search(String tenantId, Long bidProjectId,
                                           String query, String chunkType, int topK) {
        String collectionName = getCollectionName(tenantId, bidProjectId);
        return vectorStoreService.search(collectionName, query, tenantId, 0L, chunkType, topK);
    }

    /**
     * 检索所有类型
     */
    public List<VectorSearchResult> searchAll(String tenantId, Long bidProjectId,
                                              String query, int topK) {
        String collectionName = getCollectionName(tenantId, bidProjectId);
        return vectorStoreService.search(collectionName, query, tenantId, 0L, null, topK);
    }

    /**
     * 删除整个collection（项目删除时调用）
     */
    public void dropCollection(String tenantId, Long bidProjectId) {
        String collectionName = getCollectionName(tenantId, bidProjectId);
        vectorStoreService.dropCollection(collectionName);
        log.info("已删除项目collection: {}", collectionName);
    }

    /**
     * 构建VectorDocument
     */
    private VectorDocument buildDocument(String tenantId, String docType, String content,
                                         Map<String, Object> metadata) {
        return VectorDocument.builder()
            .id(UUID.randomUUID().toString())
            .tenantId(tenantId)
            .companyId(0L)
            .docType(docType)
            .content(content)
            .metadata(metadata)
            .createTime(System.currentTimeMillis())
            .build();
    }

    /**
     * 智能分块算法
     * 优先在段落边界、句号、分号处切分
     *
     * @param text      原始文本
     * @param chunkSize 目标块大小（字符数）
     * @param overlap   重叠大小（字符数）
     * @return 分块列表
     */
    private List<String> smartChunk(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        List<String> chunks = new ArrayList<>();
        int len = text.length();

        if (len <= chunkSize) {
            chunks.add(text.trim());
            return chunks;
        }

        int start = 0;
        while (start < len) {
            int end = Math.min(start + chunkSize, len);

            // 如果不是最后一块，尝试在自然边界处切分
            if (end < len) {
                int bestBreak = findBestBreakPoint(text, start + chunkSize / 2, end);
                if (bestBreak > start) {
                    end = bestBreak;
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            // 下一块的起始位置（减去重叠部分）
            start = end - overlap;
            if (start >= len) {
                break;
            }
            // 避免死循环
            if (end == len) {
                break;
            }
        }

        return chunks;
    }

    /**
     * 在给定范围内找最佳切分点
     * 优先级：段落分隔 > 句号/问号/感叹号 > 分号 > 逗号
     */
    private int findBestBreakPoint(String text, int searchStart, int searchEnd) {
        // 从后往前搜索
        for (int i = searchEnd; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (c == '\n' && i > 0 && text.charAt(i - 1) == '\n') {
                return i + 1; // 段落分隔
            }
        }
        for (int i = searchEnd; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '？' || c == '！' || c == '.' || c == '?' || c == '!') {
                return i + 1;
            }
        }
        for (int i = searchEnd; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (c == '；' || c == ';' || c == '\n') {
                return i + 1;
            }
        }
        return searchEnd;
    }

}

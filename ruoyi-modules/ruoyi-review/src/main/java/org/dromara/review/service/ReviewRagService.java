package org.dromara.review.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.domain.VectorDocument;
import org.dromara.common.ai.domain.VectorSearchResult;
import org.dromara.common.ai.service.MilvusVectorStoreService;
import org.dromara.review.domain.*;
import org.dromara.review.mapper.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 审核知识库 RAG 服务
 * <p>
 * 负责知识的向量化入库和审核时的智能检索。
 * 三类知识分别用不同的 doc_type 存储：
 * - case: 历史案例（正例/反例）
 * - pattern: 问题模式（高频错误特征）
 * - misjudgment: 误判记录（AI纠偏反馈）
 * - document: 知识文档原文分块
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewRagService {

    private final MilvusVectorStoreService vectorStoreService;
    private final ReviewKnowledgeMapper knowledgeMapper;
    private final ReviewKnowledgeDocMapper knowledgeDocMapper;
    private final ReviewKnowledgeCaseMapper knowledgeCaseMapper;
    private final ReviewKnowledgePatternMapper knowledgePatternMapper;
    private final ReviewKnowledgeMisjudgmentMapper misjudgmentMapper;
    private final ReviewStandardKnowledgeMapper standardKnowledgeMapper;

    private static final String COLLECTION_PREFIX = "review_kb_";
    private static final int CHUNK_SIZE = 500;

    // ==================== 向量化入库 ====================

    /**
     * 向量化知识库文档
     */
    @Async
    public void indexKnowledgeDoc(Long docId) {
        ReviewKnowledgeDoc doc = knowledgeDocMapper.selectById(docId);
        if (doc == null || doc.getContentText() == null || doc.getContentText().isBlank()) {
            log.warn("[RAG] 文档不存在或内容为空: docId={}", docId);
            return;
        }

        String collectionName = getCollectionName(doc.getKnowledgeId());
        List<String> chunks = chunkText(doc.getContentText(), CHUNK_SIZE);

        List<VectorDocument> vectorDocs = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            vectorDocs.add(VectorDocument.builder()
                .id("doc_" + docId + "_chunk_" + i)
                .tenantId(getTenantId(doc.getKnowledgeId()))
                .companyId(0L)
                .docType("document")
                .content(chunks.get(i))
                .metadata(Map.of("source_doc_id", docId, "chunk_index", i, "doc_name", doc.getDocName()))
                .createTime(System.currentTimeMillis())
                .build());
        }

        boolean success = vectorStoreService.insertDocuments(collectionName, vectorDocs);
        if (success) {
            doc.setVectorStatus("synced");
            doc.setChunkCount(chunks.size());
            knowledgeDocMapper.updateById(doc);
            log.info("[RAG] 文档向量化完成: docId={}, chunks={}", docId, chunks.size());
        } else {
            doc.setVectorStatus("failed");
            knowledgeDocMapper.updateById(doc);
            log.error("[RAG] 文档向量化失败: docId={}", docId);
        }
    }

    /**
     * 向量化历史案例
     */
    @Async
    public void indexCase(Long caseId) {
        ReviewKnowledgeCase kcase = knowledgeCaseMapper.selectById(caseId);
        if (kcase == null) return;

        String content = buildCaseText(kcase);
        String collectionName = getCollectionName(kcase.getKnowledgeId());

        VectorDocument doc = VectorDocument.builder()
            .id("case_" + caseId)
            .tenantId(getTenantId(kcase.getKnowledgeId()))
            .companyId(0L)
            .docType("case")
            .content(content)
            .metadata(Map.of("case_id", caseId, "case_type", kcase.getCaseType(), "title", kcase.getTitle()))
            .createTime(System.currentTimeMillis())
            .build();

        vectorStoreService.insertDocument(collectionName, doc);
        log.info("[RAG] 案例向量化完成: caseId={}, title={}", caseId, kcase.getTitle());
    }

    /**
     * 向量化问题模式
     */
    @Async
    public void indexPattern(Long patternId) {
        ReviewKnowledgePattern pattern = knowledgePatternMapper.selectById(patternId);
        if (pattern == null) return;

        String content = "问题模式: " + pattern.getPatternName()
            + "\n描述: " + (pattern.getDescription() != null ? pattern.getDescription() : "")
            + "\n处理建议: " + (pattern.getSolution() != null ? pattern.getSolution() : "");
        String collectionName = getCollectionName(pattern.getKnowledgeId());

        VectorDocument doc = VectorDocument.builder()
            .id("pattern_" + patternId)
            .tenantId(getTenantId(pattern.getKnowledgeId()))
            .companyId(0L)
            .docType("pattern")
            .content(content)
            .metadata(Map.of("pattern_id", patternId, "frequency", pattern.getFrequency()))
            .createTime(System.currentTimeMillis())
            .build();

        vectorStoreService.insertDocument(collectionName, doc);
        log.info("[RAG] 问题模式向量化完成: patternId={}", patternId);
    }

    /**
     * 向量化误判记录
     */
    @Async
    public void indexMisjudgment(Long misjudgmentId) {
        ReviewKnowledgeMisjudgment mj = misjudgmentMapper.selectById(misjudgmentId);
        if (mj == null) return;

        String content = "误判纠正记录"
            + "\n字段: " + (mj.getFieldName() != null ? mj.getFieldName() : "")
            + "\nAI原始判断: " + (mj.getAiJudgment() != null ? mj.getAiJudgment() : "")
            + "\n人工纠正为: " + (mj.getCorrectJudgment() != null ? mj.getCorrectJudgment() : "")
            + "\n原因: " + (mj.getReason() != null ? mj.getReason() : "");
        String collectionName = getCollectionName(mj.getKnowledgeId());

        VectorDocument doc = VectorDocument.builder()
            .id("misjudgment_" + misjudgmentId)
            .tenantId(getTenantId(mj.getKnowledgeId()))
            .companyId(0L)
            .docType("misjudgment")
            .content(content)
            .metadata(Map.of("misjudgment_id", misjudgmentId, "task_id", mj.getTaskId(), "field_name", mj.getFieldName() != null ? mj.getFieldName() : ""))
            .createTime(System.currentTimeMillis())
            .build();

        vectorStoreService.insertDocument(collectionName, doc);

        mj.setIsLearned("1");
        misjudgmentMapper.updateById(mj);
        log.info("[RAG] 误判记录向量化完成: misjudgmentId={}", misjudgmentId);
    }

    // ==================== RAG 检索 ====================

    /**
     * 构建完整的知识上下文（审核时调用）
     *
     * @param standardIds 关联的标准ID列表
     * @param queryText   检索查询文本（通常是表单数据）
     * @return 结构化的知识上下文文本
     */
    public String buildEnrichedContext(List<Long> standardIds, String queryText) {
        if (standardIds.isEmpty() || queryText == null || queryText.isBlank()) {
            return buildFallbackContext(standardIds);
        }

        // 获取关联的知识库ID
        List<Long> knowledgeIds = getKnowledgeIds(standardIds);
        if (knowledgeIds.isEmpty()) {
            return "暂无关联知识库";
        }

        StringBuilder context = new StringBuilder();

        // 分类检索三类知识
        List<VectorSearchResult> caseResults = searchByType(knowledgeIds, queryText, "case", 5);
        List<VectorSearchResult> patternResults = searchByType(knowledgeIds, queryText, "pattern", 3);
        List<VectorSearchResult> misjudgmentResults = searchByType(knowledgeIds, queryText, "misjudgment", 3);

        // 组装案例上下文
        if (!caseResults.isEmpty()) {
            context.append("【历史案例参考】\n");
            for (int i = 0; i < caseResults.size(); i++) {
                VectorSearchResult r = caseResults.get(i);
                String caseType = r.getMetadata() != null ? String.valueOf(r.getMetadata().getOrDefault("case_type", "")) : "";
                context.append(i + 1).append(". [").append(caseType).append("] ").append(r.getContent()).append("\n");
            }
            context.append("\n");
        }

        // 组装问题模式上下文
        if (!patternResults.isEmpty()) {
            context.append("【高频问题模式（需特别关注）】\n");
            for (int i = 0; i < patternResults.size(); i++) {
                context.append(i + 1).append(". ").append(patternResults.get(i).getContent()).append("\n");
            }
            context.append("\n");
        }

        // 组装误判提醒上下文
        if (!misjudgmentResults.isEmpty()) {
            context.append("【⚠️ 已知误判提醒（以下情况曾被人工纠正，请避免重复）】\n");
            for (int i = 0; i < misjudgmentResults.size(); i++) {
                context.append(i + 1).append(". ").append(misjudgmentResults.get(i).getContent()).append("\n");
            }
            context.append("\n");
        }

        if (context.isEmpty()) {
            return buildFallbackContext(standardIds);
        }

        return context.toString();
    }

    /**
     * 按类型从知识库集合中检索
     */
    private List<VectorSearchResult> searchByType(List<Long> knowledgeIds, String queryText, String docType, int topK) {
        List<VectorSearchResult> allResults = new ArrayList<>();

        for (Long knowledgeId : knowledgeIds) {
            String collectionName = getCollectionName(knowledgeId);
            try {
                List<VectorSearchResult> results = vectorStoreService.search(
                    collectionName, queryText, null, null, docType, topK
                );
                allResults.addAll(results);
            } catch (Exception e) {
                log.debug("[RAG] 检索知识库失败（集合可能不存在）: collection={}, error={}", collectionName, e.getMessage());
            }
        }

        // 按相似度排序，取 topK
        return allResults.stream()
            .sorted(Comparator.comparing(VectorSearchResult::getScore))
            .limit(topK)
            .collect(Collectors.toList());
    }

    /**
     * 降级方案：Milvus 不可用时直接从数据库加载
     */
    private String buildFallbackContext(List<Long> standardIds) {
        List<Long> knowledgeIds = getKnowledgeIds(standardIds);
        if (knowledgeIds.isEmpty()) return "暂无参考经验";

        StringBuilder sb = new StringBuilder();

        // 加载案例
        List<ReviewKnowledgeCase> cases = knowledgeCaseMapper.selectList(
            Wrappers.<ReviewKnowledgeCase>lambdaQuery()
                .in(ReviewKnowledgeCase::getKnowledgeId, knowledgeIds)
                .last("LIMIT 5")
        );
        if (!cases.isEmpty()) {
            sb.append("【历史案例参考】\n");
            for (ReviewKnowledgeCase c : cases) {
                sb.append("- [").append(c.getCaseType()).append("] ").append(c.getTitle());
                if (c.getKeyPoint() != null) sb.append(": ").append(c.getKeyPoint());
                sb.append("\n");
            }
            sb.append("\n");
        }

        // 加载问题模式
        List<ReviewKnowledgePattern> patterns = knowledgePatternMapper.selectList(
            Wrappers.<ReviewKnowledgePattern>lambdaQuery()
                .in(ReviewKnowledgePattern::getKnowledgeId, knowledgeIds)
                .orderByDesc(ReviewKnowledgePattern::getFrequency)
                .last("LIMIT 3")
        );
        if (!patterns.isEmpty()) {
            sb.append("【高频问题模式】\n");
            for (ReviewKnowledgePattern p : patterns) {
                sb.append("- ").append(p.getPatternName());
                if (p.getSolution() != null) sb.append(" → ").append(p.getSolution());
                sb.append("\n");
            }
            sb.append("\n");
        }

        // 加载误判记录
        List<ReviewKnowledgeMisjudgment> misjudgments = misjudgmentMapper.selectList(
            Wrappers.<ReviewKnowledgeMisjudgment>lambdaQuery()
                .in(ReviewKnowledgeMisjudgment::getKnowledgeId, knowledgeIds)
                .last("LIMIT 3")
        );
        if (!misjudgments.isEmpty()) {
            sb.append("【⚠️ 已知误判提醒】\n");
            for (ReviewKnowledgeMisjudgment m : misjudgments) {
                sb.append("- 字段[").append(m.getFieldName()).append("]: ")
                    .append("AI判断\"").append(m.getAiJudgment()).append("\"")
                    .append(" → 纠正为\"").append(m.getCorrectJudgment()).append("\"")
                    .append(" 原因: ").append(m.getReason()).append("\n");
            }
        }

        return sb.isEmpty() ? "暂无参考经验" : sb.toString();
    }

    // ==================== 工具方法 ====================

    private List<Long> getKnowledgeIds(List<Long> standardIds) {
        if (standardIds.isEmpty()) return List.of();
        List<ReviewStandardKnowledge> skList = standardKnowledgeMapper.selectList(
            Wrappers.<ReviewStandardKnowledge>lambdaQuery().in(ReviewStandardKnowledge::getStandardId, standardIds)
        );
        return skList.stream().map(ReviewStandardKnowledge::getKnowledgeId).distinct().collect(Collectors.toList());
    }

    private String getCollectionName(Long knowledgeId) {
        return COLLECTION_PREFIX + knowledgeId;
    }

    private String getTenantId(Long knowledgeId) {
        ReviewKnowledge knowledge = knowledgeMapper.selectById(knowledgeId);
        return knowledge != null ? knowledge.getTenantId() : "000000";
    }

    private String buildCaseText(ReviewKnowledgeCase kcase) {
        StringBuilder sb = new StringBuilder();
        sb.append("案例: ").append(kcase.getTitle());
        sb.append("\n类型: ").append("positive".equals(kcase.getCaseType()) ? "正例（应通过）" : "反例（应拒绝）");
        if (kcase.getScenario() != null) sb.append("\n场景: ").append(kcase.getScenario());
        if (kcase.getReviewConclusion() != null) sb.append("\n结论: ").append(kcase.getReviewConclusion());
        if (kcase.getKeyPoint() != null) sb.append("\n要点: ").append(kcase.getKeyPoint());
        return sb.toString();
    }

    private List<String> chunkText(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        // 按段落分割优先，再按长度截断
        String[] paragraphs = text.split("\n\n");
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            if (current.length() + para.length() > chunkSize && !current.isEmpty()) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(para).append("\n\n");
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }

        return chunks;
    }
}

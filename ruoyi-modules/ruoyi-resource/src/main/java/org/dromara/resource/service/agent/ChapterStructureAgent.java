package org.dromara.resource.service.agent;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.service.AiChatService;
import org.dromara.resource.domain.BizSubmissionChapter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 章节结构生成Agent
 * 根据招标文件解析结果生成标书章节结构，支持按文档类型区分目录
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChapterStructureAgent {

    private final AiChatService aiChatService;

    /**
     * 生成章节结构（返回树形节点列表，供 Service 层递归持久化）
     * 支持按文档类型（商务标/技术标/整本标书）区分目录结构
     */
    public List<ChapterNode> generateStructureNodes(
        Long submissionId,
        Long documentId,
        DocumentParserAgent.ParseResult parseResult,
        String documentType) {

        log.info("开始生成章节结构（节点模式），submissionId: {}, documentType: {}", submissionId, documentType);

        try {
            String structurePrompt = buildStructurePrompt(parseResult, documentType);
            String structureJson = aiChatService.chat(structurePrompt);

            // 清理 AI 返回值中可能包含的 markdown 代码块标记
            structureJson = structureJson.trim();
            if (structureJson.startsWith("```")) {
                structureJson = structureJson.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
            }

            List<ChapterNode> nodes = JSON.parseArray(structureJson, ChapterNode.class);
            log.info("章节结构节点生成完成，共{}个顶级节点", nodes != null ? nodes.size() : 0);
            return nodes != null ? nodes : new ArrayList<>();

        } catch (Exception e) {
            log.error("生成章节结构节点失败", e);
            throw new RuntimeException("生成章节结构节点失败: " + e.getMessage());
        }
    }

    /**
     * 生成章节结构（兼容旧调用，默认整本标书）
     */
    public List<ChapterNode> generateStructureNodes(
        Long submissionId,
        Long documentId,
        DocumentParserAgent.ParseResult parseResult) {
        return generateStructureNodes(submissionId, documentId, parseResult, "complete");
    }

    /**
     * 生成章节结构
     */
    public List<BizSubmissionChapter> generateStructure(
        Long submissionId,
        Long documentId,
        DocumentParserAgent.ParseResult parseResult) {

        log.info("开始生成章节结构，submissionId: {}", submissionId);

        try {
            // 1. 使用AI生成章节结构
            String structurePrompt = buildStructurePrompt(parseResult, "complete");
            String structureJson = aiChatService.chat(structurePrompt);

            // 2. 解析JSON为章节列表
            List<ChapterNode> chapterNodes = JSON.parseArray(structureJson, ChapterNode.class);

            // 3. 转换为实体对象
            List<BizSubmissionChapter> chapters = new ArrayList<>();
            convertToChapters(chapterNodes, submissionId, documentId, 0L, chapters);

            log.info("章节结构生成完成，共{}个章节", chapters.size());
            return chapters;

        } catch (Exception e) {
            log.error("生成章节结构失败", e);
            throw new RuntimeException("生成章节结构失败: " + e.getMessage());
        }
    }

    /**
     * 构建章节结构生成提示词（支持文档类型）
     */
    private String buildStructurePrompt(DocumentParserAgent.ParseResult parseResult, String documentType) {
        String typeInstruction = switch (documentType != null ? documentType : "complete") {
            case "commercial" -> """
                【文档类型：商务标】
                请生成商务标的章节结构，重点包含：
                - 投标函及投标函附录
                - 商务报价/报价表
                - 法定代表人授权委托书
                - 资质证明文件（营业执照、资质等级、体系认证）
                - 业绩案例
                - 财务报表
                - 信誉承诺
                不要包含技术方案、实施计划等技术标内容。
                """;
            case "technical" -> """
                【文档类型：技术标】
                请生成技术标的章节结构，重点包含：
                - 项目理解与需求分析
                - 技术方案设计
                - 实施计划与进度安排
                - 项目管理方案
                - 质量保证措施
                - 安全保障方案
                - 人员配置与组织架构
                - 培训方案
                - 售后服务方案
                不要包含报价、资质证明等商务标内容。
                """;
            default -> """
                【文档类型：整本标书】
                请生成完整标书的章节结构，同时包含商务和技术内容。
                """;
        };

        return String.format("""
            %s

            请根据以下招标文件信息，生成标书的章节结构。

            招标文件结构：
            %s

            招标要求：
            %s

            评分标准：
            %s

            提取的模板章节：
            %s

            请生成标书的完整章节结构，以JSON数组格式返回：
            [
              {
                "chapterNo": "1",
                "title": "投标函及投标函附录",
                "level": 1,
                "type": "template",
                "children": [
                  {
                    "chapterNo": "1.1",
                    "title": "投标函",
                    "level": 2,
                    "type": "template",
                    "children": []
                  }
                ]
              },
              {
                "chapterNo": "2",
                "title": "技术方案",
                "level": 1,
                "type": "generate",
                "children": [
                  {
                    "chapterNo": "2.1",
                    "title": "项目理解",
                    "level": 2,
                    "type": "generate",
                    "children": []
                  }
                ]
              }
            ]

            要求：
            1. 章节编号使用数字格式（1, 1.1, 1.1.1）
            2. type字段：template表示使用招标文件中的模板，generate表示需要AI生成
            3. 如果招标文件中有明确的章节要求，必须包含这些章节
            4. 根据评分标准，确保重要章节都包含
            5. 章节层级不超过4层
            6. 生成完整的标书结构，包括封面、目录、正文、附件等
            """,
            typeInstruction,
            parseResult.getStructure(),
            JSON.toJSONString(parseResult.getRequirements()),
            JSON.toJSONString(parseResult.getScoringCriteria()),
            JSON.toJSONString(parseResult.getTemplates())
        );
    }

    /**
     * 递归转换章节节点为实体对象
     */
    private void convertToChapters(
        List<ChapterNode> nodes,
        Long submissionId,
        Long documentId,
        Long parentId,
        List<BizSubmissionChapter> result) {

        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        int sortOrder = 0;
        for (ChapterNode node : nodes) {
            BizSubmissionChapter chapter = new BizSubmissionChapter();
            chapter.setBidSubmissionId(submissionId);
            chapter.setSubmissionDocumentId(documentId);
            chapter.setParentId(parentId);
            chapter.setChapterNo(node.getChapterNo());
            chapter.setChapterTitle(node.getTitle());
            chapter.setChapterLevel(node.getLevel());
            chapter.setChapterType(node.getType());
            chapter.setSortOrder(sortOrder++);
            chapter.setGenerationStatus("pending");
            chapter.setGenerationProgress(0);

            result.add(chapter);

            // 递归处理子章节
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                // 注意：这里需要先保存父章节获取ID，再处理子章节
                // 实际使用时需要在Service层处理
                convertToChapters(node.getChildren(), submissionId, documentId, chapter.getId(), result);
            }
        }
    }

    /**
     * 章节节点（用于JSON解析）
     */
    public static class ChapterNode {
        private String chapterNo;
        private String title;
        private Integer level;
        private String type;
        private List<ChapterNode> children;

        // Getters and Setters
        public String getChapterNo() { return chapterNo; }
        public void setChapterNo(String chapterNo) { this.chapterNo = chapterNo; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public Integer getLevel() { return level; }
        public void setLevel(Integer level) { this.level = level; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public List<ChapterNode> getChildren() { return children; }
        public void setChildren(List<ChapterNode> children) { this.children = children; }
    }

}

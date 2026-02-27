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
 * 根据招标文件解析结果生成标书章节结构
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
     * 生成章节结构
     */
    public List<BizSubmissionChapter> generateStructure(
        Long submissionId,
        Long documentId,
        DocumentParserAgent.ParseResult parseResult) {

        log.info("开始生成章节结构，submissionId: {}", submissionId);

        try {
            // 1. 使用AI生成章节结构
            String structurePrompt = buildStructurePrompt(parseResult);
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
     * 构建章节结构生成提示词
     */
    private String buildStructurePrompt(DocumentParserAgent.ParseResult parseResult) {
        return String.format("""
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

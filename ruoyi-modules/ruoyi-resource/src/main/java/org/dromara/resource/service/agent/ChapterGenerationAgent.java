package org.dromara.resource.service.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.service.AiChatService;
import org.dromara.resource.domain.BizSubmissionChapter;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 章节生成Agent
 * 负责使用AI生成标书章节内容
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChapterGenerationAgent {

    private final AiChatService aiChatService;

    /**
     * 生成章节内容
     *
     * @param chapter 章节信息
     * @param context 生成上下文
     * @return 生成的章节内容
     */
    public String generateChapter(BizSubmissionChapter chapter, GenerationContext context) {
        log.info("开始生成章节: {} - {}", chapter.getChapterNo(), chapter.getChapterTitle());

        try {
            // 1. 构建提示词
            String prompt = buildChapterPrompt(chapter, context);

            // 2. 调用AI生成
            String content = aiChatService.chat(prompt);

            // 3. 如果是大章节（level=1），生成原因说明
            if (chapter.getChapterLevel() == 1) {
                String reasonPrompt = buildReasonPrompt(chapter, context);
                String reason = aiChatService.chat(reasonPrompt);
                content = reason + "\n\n" + content;
            }

            log.info("章节生成完成: {}", chapter.getChapterTitle());
            return content;

        } catch (Exception e) {
            log.error("生成章节失败: {}", chapter.getChapterTitle(), e);
            throw new RuntimeException("生成章节失败: " + e.getMessage());
        }
    }

    /**
     * 构建章节生成提示词
     */
    private String buildChapterPrompt(BizSubmissionChapter chapter, GenerationContext context) {
        return String.format("""
            请为投标项目生成"%s"章节的内容。

            【招标项目信息】
            项目名称：%s
            招标单位：%s
            项目类型：%s
            预算金额：%s元
            项目描述：%s

            【公司信息】
            %s

            【招标要求】
            %s

            【评分标准】
            %s

            【章节要求】
            章节编号：%s
            章节标题：%s
            章节层级：第%d级

            请生成专业、规范的标书内容，要求：
            1. 内容充实、逻辑清晰
            2. 突出公司优势和项目经验
            3. 紧扣招标要求和评分标准
            4. 使用Markdown格式
            5. 包含必要的表格、列表等结构化内容
            6. 字数适中，不少于500字

            请直接输出章节内容，不要包含章节标题（标题会自动添加）。
            """,
            chapter.getChapterTitle(),
            context.getProjectName(),
            context.getBidOrg(),
            context.getProjectType(),
            context.getBudgetAmount(),
            context.getProjectDesc(),
            formatCompanyInfo(context.getCompanyInfo()),
            formatRequirements(context.getRequirements()),
            formatScoringCriteria(context.getScoringCriteria()),
            chapter.getChapterNo(),
            chapter.getChapterTitle(),
            chapter.getChapterLevel()
        );
    }

    /**
     * 构建原因说明提示词
     */
    private String buildReasonPrompt(BizSubmissionChapter chapter, GenerationContext context) {
        return String.format("""
            请为投标项目的"%s"章节生成原因说明。

            【招标文件要求】
            %s

            【评分标准】
            %s

            请生成一段简洁的原因说明（100-200字），说明：
            1. 为什么需要这个章节
            2. 该章节如何满足招标要求
            3. 该章节在评分中的重要性

            格式示例：
            > **原因说明**：根据招标文件第X条要求，投标人需提供...。本章节将详细阐述...，以满足评分标准中...的要求。

            请直接输出原因说明内容。
            """,
            chapter.getChapterTitle(),
            formatRequirements(context.getRequirements()),
            formatScoringCriteria(context.getScoringCriteria())
        );
    }

    /**
     * 格式化公司信息
     */
    private String formatCompanyInfo(Map<String, String> companyInfo) {
        if (companyInfo == null || companyInfo.isEmpty()) {
            return "（公司信息未提供）";
        }

        StringBuilder sb = new StringBuilder();
        companyInfo.forEach((key, value) -> {
            String fieldName = key.replace("{{", "").replace("}}", "").replace("_", " ");
            sb.append(fieldName).append(": ").append(value).append("\n");
        });

        return sb.toString();
    }

    /**
     * 格式化招标要求
     */
    private String formatRequirements(java.util.List<String> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return "（招标要求未提供）";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < requirements.size(); i++) {
            sb.append(i + 1).append(". ").append(requirements.get(i)).append("\n");
        }

        return sb.toString();
    }

    /**
     * 格式化评分标准
     */
    private String formatScoringCriteria(Map<String, String> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            return "（评分标准未提供）";
        }

        StringBuilder sb = new StringBuilder();
        criteria.forEach((key, value) -> {
            sb.append("- ").append(key).append(": ").append(value).append("\n");
        });

        return sb.toString();
    }

    /**
     * 生成上下文
     */
    public static class GenerationContext {
        private String projectName;
        private String bidOrg;
        private String projectType;
        private String budgetAmount;
        private String projectDesc;
        private Map<String, String> companyInfo;
        private java.util.List<String> requirements;
        private Map<String, String> scoringCriteria;

        // Getters and Setters
        public String getProjectName() { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }
        public String getBidOrg() { return bidOrg; }
        public void setBidOrg(String bidOrg) { this.bidOrg = bidOrg; }
        public String getProjectType() { return projectType; }
        public void setProjectType(String projectType) { this.projectType = projectType; }
        public String getBudgetAmount() { return budgetAmount; }
        public void setBudgetAmount(String budgetAmount) { this.budgetAmount = budgetAmount; }
        public String getProjectDesc() { return projectDesc; }
        public void setProjectDesc(String projectDesc) { this.projectDesc = projectDesc; }
        public Map<String, String> getCompanyInfo() { return companyInfo; }
        public void setCompanyInfo(Map<String, String> companyInfo) { this.companyInfo = companyInfo; }
        public java.util.List<String> getRequirements() { return requirements; }
        public void setRequirements(java.util.List<String> requirements) { this.requirements = requirements; }
        public Map<String, String> getScoringCriteria() { return scoringCriteria; }
        public void setScoringCriteria(Map<String, String> scoringCriteria) { this.scoringCriteria = scoringCriteria; }
    }

}

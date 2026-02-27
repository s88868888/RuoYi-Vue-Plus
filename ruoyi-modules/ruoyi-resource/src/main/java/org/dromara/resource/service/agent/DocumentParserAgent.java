package org.dromara.resource.service.agent;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.dromara.common.ai.service.AiChatService;
import org.dromara.resource.domain.BizBidProjectAttachment;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档解析Agent
 * 负责解析招标文件，提取要求、模板等信息
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentParserAgent {

    private final AiChatService aiChatService;

    /**
     * 解析招标文件
     */
    public ParseResult parse(BizBidProjectAttachment attachment) {
        log.info("开始解析招标文件: {}", attachment.getAttachmentName());

        try {
            // 1. 提取文本内容
            String content = extractText(attachment);

            // 2. 使用AI分析文档结构
            String structurePrompt = buildStructurePrompt(content);
            String structureJson = aiChatService.chat(structurePrompt);

            // 3. 提取模板内容
            List<TemplateInfo> templates = extractTemplates(content);

            // 4. 提取招标要求
            List<String> requirements = extractRequirements(content);

            // 5. 提取评分标准
            Map<String, String> scoringCriteria = extractScoringCriteria(content);

            ParseResult result = new ParseResult();
            result.setContent(content);
            result.setStructure(structureJson);
            result.setTemplates(templates);
            result.setRequirements(requirements);
            result.setScoringCriteria(scoringCriteria);

            log.info("招标文件解析完成，提取到{}个模板", templates.size());
            return result;

        } catch (Exception e) {
            log.error("解析招标文件失败", e);
            throw new RuntimeException("解析招标文件失败: " + e.getMessage());
        }
    }

    /**
     * 提取文本内容
     */
    private String extractText(BizBidProjectAttachment attachment) throws Exception {
        String filePath = attachment.getFilePath();
        String fileFormat = attachment.getFileFormat();

        if ("pdf".equalsIgnoreCase(fileFormat)) {
            return extractPdfText(filePath);
        } else if ("docx".equalsIgnoreCase(fileFormat)) {
            return extractWordText(filePath);
        } else {
            throw new RuntimeException("不支持的文件格式: " + fileFormat);
        }
    }

    /**
     * 提取PDF文本
     */
    private String extractPdfText(String filePath) throws Exception {
        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * 提取Word文本
     */
    private String extractWordText(String filePath) throws Exception {
        // TODO: 使用Apache POI提取Word文本
        // XWPFDocument document = new XWPFDocument(new FileInputStream(filePath));
        // XWPFWordExtractor extractor = new XWPFWordExtractor(document);
        // return extractor.getText();
        return "";
    }

    /**
     * 构建结构分析提示词
     */
    private String buildStructurePrompt(String content) {
        return String.format("""
            请分析以下招标文件，提取文档结构信息。

            招标文件内容：
            %s

            请以JSON格式返回以下信息：
            {
              "projectName": "项目名称",
              "bidOrg": "招标单位",
              "chapters": [
                {
                  "chapterNo": "章节编号",
                  "title": "章节标题",
                  "level": 1,
                  "type": "template或generate",
                  "requirement": "该章节的具体要求"
                }
              ],
              "requirements": ["要求1", "要求2"],
              "scoringCriteria": {
                "技术方案": "30分",
                "商务报价": "40分"
              }
            }

            注意：
            1. 如果章节中包含"按以下格式填写"、"参考模板"等字样，type标记为template
            2. 如果章节需要投标人自行编写，type标记为generate
            3. 提取所有重要的招标要求
            4. 提取评分标准和权重
            """, content.length() > 10000 ? content.substring(0, 10000) + "..." : content);
    }

    /**
     * 提取模板内容
     */
    private List<TemplateInfo> extractTemplates(String content) {
        List<TemplateInfo> templates = new ArrayList<>();

        // 使用AI识别模板章节
        String templatePrompt = String.format("""
            请从以下招标文件中识别所有的模板章节（包含固定格式、范文示例的章节）。

            招标文件内容：
            %s

            请以JSON数组格式返回：
            [
              {
                "chapterNo": "章节编号",
                "title": "章节标题",
                "content": "模板内容",
                "placeholders": ["{{company_name}}", "{{register_capital}}"]
              }
            ]

            注意：
            1. 识别所有带下划线、空白框、需要填写的地方作为占位符
            2. 占位符用{{}}包裹，使用有意义的英文名称
            3. 保留模板的原始格式
            """, content.length() > 10000 ? content.substring(0, 10000) + "..." : content);

        try {
            String templatesJson = aiChatService.chat(templatePrompt);
            templates = JSON.parseArray(templatesJson, TemplateInfo.class);
        } catch (Exception e) {
            log.error("提取模板失败", e);
        }

        return templates;
    }

    /**
     * 提取招标要求
     */
    private List<String> extractRequirements(String content) {
        List<String> requirements = new ArrayList<>();

        // 使用正则匹配常见的要求关键词
        Pattern pattern = Pattern.compile("(投标人[应须必需].*?[。；])", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            requirements.add(matcher.group(1).trim());
        }

        return requirements;
    }

    /**
     * 提取评分标准
     */
    private Map<String, String> extractScoringCriteria(String content) {
        Map<String, String> criteria = new HashMap<>();

        // 使用AI提取评分标准
        String scoringPrompt = String.format("""
            请从以下招标文件中提取评分标准。

            招标文件内容：
            %s

            请以JSON格式返回：
            {
              "技术方案": "30分",
              "商务报价": "40分",
              "企业资质": "20分",
              "服务承诺": "10分"
            }
            """, content.length() > 5000 ? content.substring(0, 5000) + "..." : content);

        try {
            String criteriaJson = aiChatService.chat(scoringPrompt);
            criteria = JSON.parseObject(criteriaJson, Map.class);
        } catch (Exception e) {
            log.error("提取评分标准失败", e);
        }

        return criteria;
    }

    /**
     * 解析结果
     */
    public static class ParseResult {
        private String content;              // 原始文本内容
        private String structure;            // 结构化数据（JSON）
        private List<TemplateInfo> templates; // 提取的模板
        private List<String> requirements;   // 招标要求
        private Map<String, String> scoringCriteria; // 评分标准

        // Getters and Setters
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getStructure() { return structure; }
        public void setStructure(String structure) { this.structure = structure; }
        public List<TemplateInfo> getTemplates() { return templates; }
        public void setTemplates(List<TemplateInfo> templates) { this.templates = templates; }
        public List<String> getRequirements() { return requirements; }
        public void setRequirements(List<String> requirements) { this.requirements = requirements; }
        public Map<String, String> getScoringCriteria() { return scoringCriteria; }
        public void setScoringCriteria(Map<String, String> scoringCriteria) { this.scoringCriteria = scoringCriteria; }
    }

    /**
     * 模板信息
     */
    public static class TemplateInfo {
        private String chapterNo;
        private String title;
        private String content;
        private List<String> placeholders;

        // Getters and Setters
        public String getChapterNo() { return chapterNo; }
        public void setChapterNo(String chapterNo) { this.chapterNo = chapterNo; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public List<String> getPlaceholders() { return placeholders; }
        public void setPlaceholders(List<String> placeholders) { this.placeholders = placeholders; }
    }

}

package org.dromara.resource.service.agent;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.dromara.common.ai.service.AiChatService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档解析Agent
 * 负责解析招标文件，提取要求、模板等信息
 * 使用qwen-long传文件对象分析（不截断），本地提取全文用于Milvus分块
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
     *
     * @param ossUrl     OSS 文件访问 URL
     * @param fileFormat 文件格式（pdf / docx / doc）
     * @param fileName   文件名（用于日志）
     */
    public ParseResult parse(String ossUrl, String fileFormat, String fileName) {
        log.info("开始解析招标文件: {}", fileName);

        try {
            // 1. 下载文件为字节数组
            byte[] fileBytes = URI.create(ossUrl).toURL().openStream().readAllBytes();

            // 2. 构建文件Resource对象，传给qwen-long（不截断！）
            Resource fileResource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };

            // 3. 文档结构分析（qwen-long读完整文件）
            log.info("开始AI分析文档结构...");
            String structureJson = aiChatService.chatWithDocument(fileResource, buildStructurePrompt());

            // 4. 模板提取（qwen-long读完整文件）
            log.info("开始AI提取模板...");
            List<TemplateInfo> templates = extractTemplates(fileResource);

            // 5. 本地提取全文（用于分块入Milvus + 正则提取要求）
            String fullText = extractText(fileBytes, fileFormat);

            // 6. 招标要求（正则提取）
            List<String> requirements = extractRequirements(fullText);

            // 7. 评分标准（qwen-long分析）
            log.info("开始AI提取评分标准...");
            Map<String, String> scoringCriteria = extractScoringCriteria(fileResource);

            ParseResult result = new ParseResult();
            result.setContent(fullText);
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
     * 从字节数组提取文本内容（本地提取，用于Milvus分块和正则匹配）
     */
    private String extractText(byte[] bytes, String fileFormat) throws Exception {
        if ("pdf".equalsIgnoreCase(fileFormat)) {
            return extractPdfText(bytes);
        } else if ("docx".equalsIgnoreCase(fileFormat) || "doc".equalsIgnoreCase(fileFormat)) {
            return extractWordText(bytes);
        } else {
            throw new RuntimeException("不支持的文件格式: " + fileFormat);
        }
    }

    /**
     * 提取PDF文本
     */
    private String extractPdfText(byte[] bytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * 提取Word文本（使用 Apache POI）
     */
    private String extractWordText(byte[] bytes) throws Exception {
        try (InputStream is = new ByteArrayInputStream(bytes);
             XWPFDocument document = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    /**
     * 构建结构分析提示词（纯任务描述，不拼接文档内容，qwen-long自己读文件）
     */
    private String buildStructurePrompt() {
        return """
            请分析附件中的招标文件，提取文档结构信息。

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
            """;
    }

    /**
     * 提取模板内容（使用qwen-long读完整文件）
     */
    private List<TemplateInfo> extractTemplates(Resource fileResource) {
        List<TemplateInfo> templates = new ArrayList<>();

        String templatePrompt = """
            请从附件招标文件中识别所有的模板章节（包含固定格式、范文示例的章节）。

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
            """;

        try {
            String templatesJson = aiChatService.chatWithDocument(fileResource, templatePrompt);
            // 清理markdown代码块标记
            templatesJson = templatesJson.trim();
            if (templatesJson.startsWith("```")) {
                templatesJson = templatesJson.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
            }
            templates = JSON.parseArray(templatesJson, TemplateInfo.class);
        } catch (Exception e) {
            log.error("提取模板失败", e);
        }

        return templates;
    }

    /**
     * 提取招标要求（正则提取，使用本地全文）
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
     * 提取评分标准（使用qwen-long读完整文件）
     */
    private Map<String, String> extractScoringCriteria(Resource fileResource) {
        Map<String, String> criteria = new HashMap<>();

        String scoringPrompt = """
            请从附件招标文件中提取评分标准。

            请以JSON格式返回：
            {
              "技术方案": "30分",
              "商务报价": "40分",
              "企业资质": "20分",
              "服务承诺": "10分"
            }
            """;

        try {
            String criteriaJson = aiChatService.chatWithDocument(fileResource, scoringPrompt);
            // 清理markdown代码块标记
            criteriaJson = criteriaJson.trim();
            if (criteriaJson.startsWith("```")) {
                criteriaJson = criteriaJson.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
            }
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

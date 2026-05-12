package org.dromara.review.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.service.AiChatService;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.oss.core.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.review.domain.vo.ParsedRuleVo;
import org.dromara.review.domain.vo.ReviewRuleImportVo;
import org.dromara.system.domain.vo.SysOssVo;
import org.dromara.system.service.ISysOssService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 审核规则抽取/导入 Service
 * <p>
 * - AI 抽取：qwen-long 读取 Word/PDF/TXT，输出结构化 JSON 规则
 * - Excel 导入：EasyExcel 解析模板填写的规则
 * - 模板下载：导出带表头/示例/说明的 xlsx
 *
 * @author ruoyi
 * @date 2026-05-12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewStandardParseService {

    private final AiChatService aiChatService;
    private final ISysOssService ossService;

    private static final Set<String> SEVERITY_VALUES = Set.of("must", "should", "suggest");
    private static final Set<String> CHECK_METHODS = Set.of("ai_extract", "compare", "exact_match", "keyword");
    private static final int MAX_CONTENT_LEN = 300;

    private static final String EXTRACT_SYSTEM_PROMPT = """
        你是审核规则抽取助手。请从附件规范文档中提取所有可执行的审核条款，
        输出严格的 JSON 数组，**不要任何额外文字、不要 markdown 代码块、不要注释**。

        严格要求：
        - 字段名必须用英文：content、severity、category，不得翻译为中文
        - 每一条对象必须同时包含 content、severity、category 三个字段，不得缺省
        - severity 的值必须是英文小写：must / should / suggest，不得用中文或其他值

        字段释义：
        - content: 规则的完整描述（10~80 字，单句，祈使或陈述句）
        - severity: 严重程度，取值仅限 must/should/suggest
          * must: 原文出现"必须/不得/禁止/严格按照/应当严格"等强制性词汇
          * should: 原文出现"应/应当/宜/原则上"等推荐性词汇
          * suggest: 原文出现"可/建议/鼓励/倡导"等建议性词汇
        - category: 规则所属业务分类，用中文即可（如"主体信息/金额条款/付款条款/期限条款/验收条款/违约条款/知识产权/保密条款/争议解决/格式规范"）

        处理规则：
        1. 一条规则只查一件事，多条款要拆分独立的审核点
        2. 跳过纯背景描述、目录、页眉页脚、总则性说明等非规则内容
        3. 如无任何可提取规则，返回 []

        输出示例（只能输出如下格式，不许加其他内容）：
        [
          {"content":"合同必须包含完整的甲乙双方主体信息","severity":"must","category":"主体信息"},
          {"content":"合同金额大写与小写必须完全一致","severity":"must","category":"金额条款"},
          {"content":"应明确争议解决方式及管辖法院","severity":"should","category":"争议解决"},
          {"content":"建议补充仲裁作为争议解决备选方式","severity":"suggest","category":"争议解决"}
        ]
        """;

    private static final String EXTRACT_USER_PROMPT = "请提取附件中所有审核规则，输出 JSON 数组。";

    /**
     * 调用 qwen-long 从 OSS 文件抽取规则
     */
    public List<ParsedRuleVo> parseDocument(Long ossId) {
        SysOssVo ossVo = TenantHelper.ignore(() -> ossService.getById(ossId));
        if (ossVo == null) {
            throw new RuntimeException("OSS 文件不存在: " + ossId);
        }
        OssClient storage = OssFactory.instance(ossVo.getService());
        Path tempFile = storage.fileDownload(ossVo.getFileName());
        log.info("[ParseService] 文件已下载到: {}", tempFile);

        Resource resource = new FileSystemResource(tempFile.toFile());
        String raw = aiChatService.chatWithDocument(EXTRACT_SYSTEM_PROMPT, resource, EXTRACT_USER_PROMPT);
        log.info("[ParseService] AI 原始返回长度={}, 内容={}", raw == null ? 0 : raw.length(), raw);

        return parseJsonArray(raw);
    }

    /**
     * 从 Excel 导入规则
     */
    public List<ParsedRuleVo> importTemplate(InputStream inputStream) {
        List<ReviewRuleImportVo> rows = ExcelUtil.importExcel(inputStream, ReviewRuleImportVo.class);
        List<ParsedRuleVo> result = new ArrayList<>();
        if (rows == null) return result;
        for (ReviewRuleImportVo row : rows) {
            ParsedRuleVo vo = new ParsedRuleVo();
            vo.setContent(trim(row.getContent()));
            vo.setSeverity(normalizeSeverity(row.getSeverity()));
            vo.setCategory(trim(row.getCategory()));
            // 导入模板不再暴露 checkField/checkMethod/weight，统一走默认值
            vo.setCheckField(null);
            vo.setCheckMethod("ai_extract");
            vo.setWeight(10);
            vo.setRemark(trim(row.getRemark()));
            if (vo.getContent() == null || vo.getContent().isBlank()) continue;
            result.add(vo);
        }
        return result;
    }

    /**
     * 下载模板（xlsx）
     * <p>
     * 3 个 sheet：
     *  - 规则清单：给用户填写的表，带示例行 + 严重程度下拉
     *  - 填写说明：字段含义、严重程度释义、常见分类参考
     *  - 示例参考：一份完整示例供用户对照
     */
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String fileName = URLEncoder.encode("审核规则导入模板.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName);

        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            buildRuleSheet(wb);
            buildGuideSheet(wb);
            buildExampleSheet(wb);
            wb.write(response.getOutputStream());
        }
    }

    private void buildRuleSheet(org.apache.poi.xssf.usermodel.XSSFWorkbook wb) {
        org.apache.poi.xssf.usermodel.XSSFSheet sheet = wb.createSheet("规则清单");
        sheet.setDefaultColumnWidth(20);
        sheet.setColumnWidth(0, 60 * 256);  // 规则内容 宽
        sheet.setColumnWidth(1, 14 * 256);
        sheet.setColumnWidth(2, 18 * 256);
        sheet.setColumnWidth(3, 30 * 256);

        org.apache.poi.ss.usermodel.CellStyle headerStyle = buildHeaderStyle(wb);
        org.apache.poi.ss.usermodel.CellStyle requiredStyle = buildHeaderStyle(wb);
        org.apache.poi.ss.usermodel.Font redBoldFont = wb.createFont();
        redBoldFont.setBold(true);
        redBoldFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
        requiredStyle.setFont(redBoldFont);

        String[] headers = {"规则内容（必填）", "严重程度（必填）", "规则分类", "备注"};
        org.apache.poi.ss.usermodel.Row head = sheet.createRow(0);
        head.setHeightInPoints(22);
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell c = head.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(i <= 1 ? requiredStyle : headerStyle);
        }

        // 示例行（用浅灰样式 + 前缀"示例："提示用户是样例）
        org.apache.poi.ss.usermodel.CellStyle sampleStyle = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font gray = wb.createFont();
        gray.setColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_50_PERCENT.getIndex());
        gray.setItalic(true);
        sampleStyle.setFont(gray);
        sampleStyle.setWrapText(true);

        Object[][] samples = {
            {"合同必须包含完整的甲乙双方主体信息及统一社会信用代码", "must", "主体信息", "示例：强制性条款，可删除"},
            {"合同金额大写与小写必须完全一致", "must", "金额条款", "示例：跨字段比对"},
            {"应明确争议解决方式及管辖法院", "should", "争议解决", "示例：推荐性条款"},
            {"建议补充仲裁作为争议解决备选方式", "suggest", "争议解决", "示例：建议性条款"},
        };
        for (int i = 0; i < samples.length; i++) {
            org.apache.poi.ss.usermodel.Row r = sheet.createRow(i + 1);
            r.setHeightInPoints(28);
            for (int j = 0; j < samples[i].length; j++) {
                org.apache.poi.ss.usermodel.Cell c = r.createCell(j);
                c.setCellValue(String.valueOf(samples[i][j]));
                c.setCellStyle(sampleStyle);
            }
        }

        // 严重程度下拉（对 B 列整列生效）
        org.apache.poi.ss.usermodel.DataValidationHelper dvHelper = sheet.getDataValidationHelper();
        org.apache.poi.ss.usermodel.DataValidationConstraint constraint = dvHelper.createExplicitListConstraint(
            new String[]{"must", "should", "suggest", "必须", "应当", "建议"});
        org.apache.poi.ss.util.CellRangeAddressList range = new org.apache.poi.ss.util.CellRangeAddressList(1, 1000, 1, 1);
        org.apache.poi.ss.usermodel.DataValidation validation = dvHelper.createValidation(constraint, range);
        validation.setShowErrorBox(true);
        validation.createErrorBox("输入错误", "严重程度只能填：must/should/suggest 或 必须/应当/建议");
        sheet.addValidationData(validation);
    }

    private void buildGuideSheet(org.apache.poi.xssf.usermodel.XSSFWorkbook wb) {
        org.apache.poi.xssf.usermodel.XSSFSheet sheet = wb.createSheet("填写说明");
        sheet.setDefaultColumnWidth(32);
        sheet.setColumnWidth(0, 20 * 256);
        sheet.setColumnWidth(1, 80 * 256);

        org.apache.poi.ss.usermodel.CellStyle titleStyle = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font titleFont = wb.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 12);
        titleStyle.setFont(titleFont);

        org.apache.poi.ss.usermodel.CellStyle wrapStyle = wb.createCellStyle();
        wrapStyle.setWrapText(true);
        wrapStyle.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.TOP);

        String[][] rows = {
            {"字段", "说明"},
            {"规则内容（必填）", "一条规则只查一件事。用完整的句子，以「必须/应当/不得/建议」等词表达。示例：合同金额大写与小写必须完全一致。"},
            {"严重程度（必填）", "must = 必须（强制性，违反即不通过）；should = 应当（推荐性，违反会告警）；suggest = 建议（仅提示，不影响通过）。也支持中文填写。"},
            {"规则分类", "用于分组统计，选填。参考：主体信息 / 金额条款 / 期限条款 / 付款条款 / 验收条款 / 违约条款 / 知识产权 / 保密条款 / 争议解决 / 格式规范 / 其他。"},
            {"备注", "选填，记录规则来源、适用范围等辅助信息。"},
            {"", ""},
            {"如何写好规则？", "1. 一条规则只查一件事，便于 AI 精准判断\n2. 用具体值而非模糊词，如\"金额大写小写一致\"而不是\"金额规范\"\n3. 避免跨段落组合条款，拆成多条\n4. 严重程度要对应原规范的强度用词"},
        };
        for (int i = 0; i < rows.length; i++) {
            org.apache.poi.ss.usermodel.Row r = sheet.createRow(i);
            r.setHeightInPoints(i == 0 ? 22 : (i == rows.length - 1 ? 80 : 40));
            for (int j = 0; j < rows[i].length; j++) {
                org.apache.poi.ss.usermodel.Cell c = r.createCell(j);
                c.setCellValue(rows[i][j]);
                c.setCellStyle(i == 0 ? titleStyle : wrapStyle);
            }
        }
    }

    private void buildExampleSheet(org.apache.poi.xssf.usermodel.XSSFWorkbook wb) {
        org.apache.poi.xssf.usermodel.XSSFSheet sheet = wb.createSheet("示例参考");
        sheet.setDefaultColumnWidth(24);
        sheet.setColumnWidth(0, 60 * 256);
        sheet.setColumnWidth(1, 14 * 256);
        sheet.setColumnWidth(2, 18 * 256);
        sheet.setColumnWidth(3, 24 * 256);

        org.apache.poi.ss.usermodel.CellStyle headerStyle = buildHeaderStyle(wb);
        String[] headers = {"规则内容", "严重程度", "规则分类", "备注"};
        org.apache.poi.ss.usermodel.Row head = sheet.createRow(0);
        head.setHeightInPoints(22);
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell c = head.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }
        Object[][] data = {
            {"合同必须包含完整的甲乙双方主体信息及统一社会信用代码", "must", "主体信息", ""},
            {"合同金额大写与小写必须完全一致", "must", "金额条款", ""},
            {"服务期限必须明确起止日期，不得仅写时长", "must", "期限条款", ""},
            {"付款方式必须明确各期比例、金额及触发条件", "must", "付款条款", ""},
            {"必须包含违约责任条款，且应双向约定", "must", "违约条款", ""},
            {"验收条款应明确验收方式、时限和标准文件编号", "should", "验收条款", ""},
            {"应包含知识产权归属条款，覆盖第三方组件", "should", "知识产权", ""},
            {"应包含保密条款并明确保密期限", "should", "保密条款", ""},
            {"应明确争议解决方式及管辖法院", "should", "争议解决", ""},
            {"格式应符合标准公文要求（字体、字号、行距）", "should", "格式规范", ""},
            {"建议补充仲裁作为争议解决备选方式", "suggest", "争议解决", ""},
        };
        org.apache.poi.ss.usermodel.CellStyle bodyStyle = wb.createCellStyle();
        bodyStyle.setWrapText(true);
        for (int i = 0; i < data.length; i++) {
            org.apache.poi.ss.usermodel.Row r = sheet.createRow(i + 1);
            r.setHeightInPoints(24);
            for (int j = 0; j < data[i].length; j++) {
                org.apache.poi.ss.usermodel.Cell c = r.createCell(j);
                c.setCellValue(String.valueOf(data[i][j]));
                c.setCellStyle(bodyStyle);
            }
        }
    }

    private org.apache.poi.ss.usermodel.CellStyle buildHeaderStyle(org.apache.poi.xssf.usermodel.XSSFWorkbook wb) {
        org.apache.poi.ss.usermodel.CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_40_PERCENT.getIndex());
        style.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER);
        style.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setBold(true);
        font.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
        style.setFont(font);
        return style;
    }

    // ==================== 内部方法 ====================

    private List<ParsedRuleVo> parseJsonArray(String raw) {
        List<ParsedRuleVo> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) return result;

        String json = extractJsonArray(raw);
        if (json == null) {
            log.warn("[ParseService] 未从 AI 返回中找到 JSON 数组");
            return result;
        }

        JSONArray arr;
        try {
            arr = JSON.parseArray(json);
        } catch (Exception e) {
            log.warn("[ParseService] JSON 解析失败: {}", e.getMessage());
            return result;
        }
        log.info("[ParseService] JSON 数组长度={}", arr == null ? -1 : arr.size());
        int skipEmpty = 0, skipTooLong = 0;
        for (int i = 0; i < arr.size(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            if (obj == null) continue;
            // 兼容 snake_case / camelCase / 中文
            String content = trim(firstNonBlank(
                obj.getString("content"),
                obj.getString("rule_content"),
                obj.getString("ruleContent"),
                obj.getString("内容"),
                obj.getString("规则内容"),
                obj.getString("rule"),
                obj.getString("text")));
            if (content == null || content.isBlank()) { skipEmpty++; continue; }
            if (content.length() > MAX_CONTENT_LEN) { skipTooLong++; continue; }
            ParsedRuleVo vo = new ParsedRuleVo();
            vo.setContent(content);
            vo.setSeverity(normalizeSeverity(firstNonBlank(
                obj.getString("severity"),
                obj.getString("level"),
                obj.getString("type"),
                obj.getString("priority"),
                obj.getString("importance"),
                obj.getString("严重程度"),
                obj.getString("等级"),
                obj.getString("级别"))));
            vo.setCategory(trim(firstNonBlank(
                obj.getString("category"),
                obj.getString("class"),
                obj.getString("classification"),
                obj.getString("group"),
                obj.getString("分类"),
                obj.getString("规则分类"),
                obj.getString("类别"))));
            vo.setCheckField(trim(firstNonBlank(obj.getString("checkField"), obj.getString("check_field"))));
            vo.setCheckMethod(normalizeCheckMethod(firstNonBlank(obj.getString("checkMethod"), obj.getString("check_method"))));
            vo.setWeight(10);
            result.add(vo);
        }
        log.info("[ParseService] 成功解析规则 {} 条 (跳过 空content={}, 超长={})", result.size(), skipEmpty, skipTooLong);
        return result;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private String extractJsonArray(String raw) {
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) return null;
        return raw.substring(start, end + 1);
    }

    private String normalizeSeverity(String s) {
        if (s == null) return "should";
        String v = s.trim().toLowerCase();
        if (SEVERITY_VALUES.contains(v)) return v;
        // 兼容中文填写
        return switch (s.trim()) {
            case "必须", "强制", "严重" -> "must";
            case "建议", "可选", "提示" -> "suggest";
            case "应当", "推荐", "警告" -> "should";
            default -> "should";
        };
    }

    private String normalizeCheckMethod(String s) {
        if (s == null || s.isBlank()) return "ai_extract";
        String v = s.trim().toLowerCase();
        return CHECK_METHODS.contains(v) ? v : "ai_extract";
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }
}
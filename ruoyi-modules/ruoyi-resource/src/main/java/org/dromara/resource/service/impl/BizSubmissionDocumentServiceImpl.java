package org.dromara.resource.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.file.FileUtils;
import org.dromara.resource.domain.BizBidProject;
import org.dromara.resource.domain.BizBidSubmission;
import org.dromara.resource.domain.BizDocumentConfig;
import org.dromara.resource.domain.BizSubmissionChapter;
import org.dromara.resource.domain.BizSubmissionDocument;
import org.dromara.resource.domain.vo.BizSubmissionDocumentVo;
import org.dromara.resource.mapper.BizBidProjectMapper;
import org.dromara.resource.mapper.BizBidSubmissionMapper;
import org.dromara.resource.mapper.BizDocumentConfigMapper;
import org.dromara.resource.mapper.BizSubmissionChapterMapper;
import org.dromara.resource.mapper.BizSubmissionDocumentMapper;
import org.dromara.resource.service.IBizSubmissionDocumentService;
import org.dromara.system.domain.vo.SysOssVo;
import org.dromara.system.service.ISysOssService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 标书文档版本Service业务层处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizSubmissionDocumentServiceImpl implements IBizSubmissionDocumentService {

    private final BizSubmissionDocumentMapper baseMapper;
    private final BizSubmissionChapterMapper chapterMapper;
    private final BizDocumentConfigMapper documentConfigMapper;
    private final BizBidSubmissionMapper submissionMapper;
    private final BizBidProjectMapper bidProjectMapper;
    private final ISysOssService sysOssService;

    @Override
    public List<BizSubmissionDocumentVo> listLatestBySubmissionId(Long submissionId) {
        return baseMapper.selectLatestBySubmissionId(submissionId);
    }

    @Override
    public List<BizSubmissionDocumentVo> listVersionsByConfigId(Long documentConfigId) {
        return baseMapper.selectVersionsByConfigId(documentConfigId);
    }

    @Override
    public List<BizSubmissionDocumentVo> listAllBySubmissionId(Long submissionId) {
        return baseMapper.selectAllBySubmissionId(submissionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizSubmissionDocumentVo saveAllVersion(Long submissionId) {
        // 1. 查询该投标项目下所有章节
        LambdaQueryWrapper<BizSubmissionChapter> chapterQuery = Wrappers.lambdaQuery();
        chapterQuery.eq(BizSubmissionChapter::getBidSubmissionId, submissionId);
        List<BizSubmissionChapter> allChapters = chapterMapper.selectList(chapterQuery);

        if (allChapters == null || allChapters.isEmpty()) {
            throw new ServiceException("暂无章节内容可归纳");
        }

        // 2. 查询文档配置，按 document_no 排序，用于分组标题
        LambdaQueryWrapper<BizDocumentConfig> configQuery = Wrappers.lambdaQuery();
        configQuery.eq(BizDocumentConfig::getBidSubmissionId, submissionId);
        configQuery.orderByAsc(BizDocumentConfig::getDocumentNo);
        List<BizDocumentConfig> configs = documentConfigMapper.selectList(configQuery);

        // 3. 按 submission_document_id 分组章节
        Map<Long, List<BizSubmissionChapter>> chaptersByDocId = allChapters.stream()
            .collect(Collectors.groupingBy(BizSubmissionChapter::getSubmissionDocumentId));

        // 4. 按文档配置顺序，分组构建 Markdown（每组内按树形层级排序）
        StringBuilder sb = new StringBuilder();
        for (BizDocumentConfig config : configs) {
            List<BizSubmissionChapter> docChapters = chaptersByDocId.get(config.getId());
            if (docChapters == null || docChapters.isEmpty()) {
                continue;
            }
            // 添加文档分隔标题
            String docTitle = config.getDocumentName() != null ? config.getDocumentName()
                : (getDocumentTypeLabel(config.getDocumentType()) + (config.getDocumentNo() != null ? " " + config.getDocumentNo() : ""));
            sb.append("---\n\n# ").append(docTitle).append("\n\n");
            // 按树形层级排序后构建内容
            List<BizSubmissionChapter> sorted = buildTreeOrder(docChapters);
            sb.append(buildMarkdownContent(sorted));
        }

        // 添加附件信息到内容末尾
        String attachmentText = buildAttachmentText(submissionId);
        if (StrUtil.isNotBlank(attachmentText)) {
            sb.append("\n\n").append(attachmentText);
        }

        String markdownContent = sb.toString();

        // 3. 查询当前最大版本号（整本合并文档，documentConfigId 为空）
        LambdaQueryWrapper<BizSubmissionDocument> versionQuery = Wrappers.lambdaQuery();
        versionQuery.eq(BizSubmissionDocument::getBidSubmissionId, submissionId);
        versionQuery.eq(BizSubmissionDocument::getDocumentType, "complete");
        versionQuery.isNull(BizSubmissionDocument::getDocumentConfigId);
        List<BizSubmissionDocument> existingDocs = baseMapper.selectList(versionQuery);
        int maxVersion = existingDocs.stream()
            .mapToInt(v -> v.getVersion() != null ? v.getVersion() : 0)
            .max()
            .orElse(0);

        // 4. 将旧版本的 is_latest 更新为 '0'
        if (!existingDocs.isEmpty()) {
            LambdaUpdateWrapper<BizSubmissionDocument> updateWrapper = Wrappers.lambdaUpdate();
            updateWrapper.eq(BizSubmissionDocument::getBidSubmissionId, submissionId);
            updateWrapper.eq(BizSubmissionDocument::getDocumentType, "complete");
            updateWrapper.isNull(BizSubmissionDocument::getDocumentConfigId);
            updateWrapper.set(BizSubmissionDocument::getIsLatest, "0");
            baseMapper.update(null, updateWrapper);
        }

        // 5. 插入新版本记录
        BizSubmissionDocument newDoc = new BizSubmissionDocument();
        newDoc.setBidSubmissionId(submissionId);
        newDoc.setDocumentName("完整标书");
        newDoc.setDocumentType("complete");
        newDoc.setDocumentContent(markdownContent);
        newDoc.setVersion(maxVersion + 1);
        newDoc.setIsLatest("1");
        newDoc.setGenerationStatus("completed");
        newDoc.setGenerationProgress(100);

        baseMapper.insert(newDoc);

        return baseMapper.selectVoById(newDoc.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizSubmissionDocumentVo saveVersion(Long documentConfigId, Long submissionId) {
        // 1. 查询文档配置信息
        BizDocumentConfig config = documentConfigMapper.selectById(documentConfigId);
        if (config == null) {
            throw new ServiceException("文档配置不存在");
        }

        // 2. 查询所有章节内容，合并为 Markdown
        LambdaQueryWrapper<BizSubmissionChapter> lqw = Wrappers.lambdaQuery();
        lqw.eq(BizSubmissionChapter::getBidSubmissionId, submissionId);
        lqw.eq(BizSubmissionChapter::getSubmissionDocumentId, documentConfigId);
        List<BizSubmissionChapter> chapters = chapterMapper.selectList(lqw);

        // 按树形层级排序后构建 Markdown
        List<BizSubmissionChapter> sorted = buildTreeOrder(chapters);
        String markdownContent = buildMarkdownContent(sorted);

        // 添加附件信息到内容末尾
        String attachmentText = buildAttachmentText(submissionId);
        if (StrUtil.isNotBlank(attachmentText)) {
            markdownContent += "\n\n" + attachmentText;
        }

        // 3. 查询当前最大版本号
        List<BizSubmissionDocumentVo> existingVersions = baseMapper.selectVersionsByConfigId(documentConfigId);
        int maxVersion = existingVersions.stream()
            .mapToInt(v -> v.getVersion() != null ? v.getVersion() : 0)
            .max()
            .orElse(0);

        // 4. 将旧版本的 is_latest 更新为 '0'
        LambdaUpdateWrapper<BizSubmissionDocument> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.eq(BizSubmissionDocument::getDocumentConfigId, documentConfigId);
        updateWrapper.set(BizSubmissionDocument::getIsLatest, "0");
        baseMapper.update(null, updateWrapper);

        // 5. 插入新版本记录
        BizSubmissionDocument newDoc = new BizSubmissionDocument();
        newDoc.setBidSubmissionId(submissionId);
        newDoc.setDocumentConfigId(documentConfigId);
        newDoc.setCompanyId(config.getCompanyId());
        newDoc.setCompanyName(config.getCompanyName());
        newDoc.setDocumentName(config.getDocumentName() != null ? config.getDocumentName()
            : (config.getCompanyName() + "_" + getDocumentTypeLabel(config.getDocumentType())));
        newDoc.setDocumentType(config.getDocumentType());
        newDoc.setDocumentNo(config.getDocumentNo());
        newDoc.setDocumentContent(markdownContent);
        newDoc.setVersion(maxVersion + 1);
        newDoc.setIsLatest("1");
        newDoc.setGenerationStatus("completed");
        newDoc.setGenerationProgress(100);

        baseMapper.insert(newDoc);

        return baseMapper.selectVoById(newDoc.getId());
    }

    @Override
    public void exportDocument(Long documentId, String format, HttpServletResponse response) {
        BizSubmissionDocumentVo doc = baseMapper.selectVoById(documentId);
        if (doc == null) {
            throw new ServiceException("文档不存在");
        }

        String content = doc.getDocumentContent();
        if (StrUtil.isBlank(content)) {
            throw new ServiceException("文档内容为空，请先保存版本");
        }

        String docName = (doc.getDocumentName() != null ? doc.getDocumentName() : "标书文档")
            + "_v" + doc.getVersion();

        try {
            if ("pdf".equalsIgnoreCase(format)) {
                exportAsPdf(content, docName, response);
            } else {
                exportAsDocx(content, docName, response);
            }
        } catch (IOException e) {
            log.error("导出文档失败, documentId: {}", documentId, e);
            throw new ServiceException("导出文档失败: " + e.getMessage());
        }

        // 导出成功后，更新标书配置状态为"已导出"，并重算投标项目整体进度
        if (doc.getDocumentConfigId() != null) {
            try {
                BizDocumentConfig configUpdate = new BizDocumentConfig();
                configUpdate.setId(doc.getDocumentConfigId());
                configUpdate.setGenerationStatus("exported");
                documentConfigMapper.updateById(configUpdate);
                if (doc.getBidSubmissionId() != null) {
                    recalculateSubmissionProgress(doc.getBidSubmissionId());
                }
            } catch (Exception e) {
                log.warn("更新标书配置状态(exported)失败: {}", e.getMessage());
            }
        }
    }

    // ----------------------------- 私有方法 -----------------------------

    /**
     * 根据各标书配置状态重新计算投标项目整体进度
     * pending=0, structure_generated=33, content_generated=66, exported=100
     */
    private void recalculateSubmissionProgress(Long submissionId) {
        try {
            List<BizDocumentConfig> configs = documentConfigMapper.selectList(
                Wrappers.lambdaQuery(BizDocumentConfig.class)
                    .eq(BizDocumentConfig::getBidSubmissionId, submissionId)
                    .eq(BizDocumentConfig::getStatus, "active")
            );
            if (configs.isEmpty()) return;
            int totalScore = 0;
            for (BizDocumentConfig config : configs) {
                String s = config.getGenerationStatus();
                totalScore += switch (s != null ? s : "") {
                    case "structure_generated" -> 33;
                    case "content_generated"   -> 66;
                    case "exported"            -> 100;
                    default                    -> 0;
                };
            }
            int avgProgress = totalScore / configs.size();
            BizBidSubmission progressUpdate = new BizBidSubmission();
            progressUpdate.setId(submissionId);
            progressUpdate.setGenerationProgress(avgProgress);
            submissionMapper.updateById(progressUpdate);
        } catch (Exception e) {
            log.warn("重新计算投标项目进度失败: {}", e.getMessage());
        }
    }

    /**
     * 按树形结构深度优先排序章节列表
     * sort_order 只在同级兄弟节点内有效，需要递归构建正确的全局顺序
     */
    private List<BizSubmissionChapter> buildTreeOrder(List<BizSubmissionChapter> chapters) {
        if (chapters == null || chapters.isEmpty()) {
            return List.of();
        }
        // 按 parent_id 分组
        Map<Long, List<BizSubmissionChapter>> childrenMap = chapters.stream()
            .collect(Collectors.groupingBy(c -> c.getParentId() != null ? c.getParentId() : 0L));
        // 每组内按 sort_order 排序
        childrenMap.values().forEach(list -> list.sort(Comparator.comparingInt(
            c -> c.getSortOrder() != null ? c.getSortOrder() : 0)));
        // DFS 遍历
        List<BizSubmissionChapter> result = new ArrayList<>();
        dfsCollect(0L, childrenMap, result);
        return result;
    }

    private void dfsCollect(Long parentId, Map<Long, List<BizSubmissionChapter>> childrenMap, List<BizSubmissionChapter> result) {
        List<BizSubmissionChapter> children = childrenMap.get(parentId);
        if (children == null) {
            return;
        }
        for (BizSubmissionChapter child : children) {
            result.add(child);
            dfsCollect(child.getId(), childrenMap, result);
        }
    }

    private String buildMarkdownContent(List<BizSubmissionChapter> chapters) {
        if (chapters == null || chapters.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (BizSubmissionChapter chapter : chapters) {
            // 根据层级确定 Markdown 标题级别
            int level = chapter.getChapterLevel() != null ? chapter.getChapterLevel() : 1;
            String prefix = "#".repeat(Math.min(level, 6));
            String title = StrUtil.blankToDefault(chapter.getChapterNo(), "")
                + " " + StrUtil.blankToDefault(chapter.getChapterTitle(), "");
            sb.append(prefix).append(" ").append(title.trim()).append("\n\n");
            if (StrUtil.isNotBlank(chapter.getChapterContent())) {
                sb.append(chapter.getChapterContent().trim()).append("\n\n");
            }
        }
        return sb.toString();
    }

    /**
     * 导出为 Word（.docx）
     */
    private static final Pattern IMG_SRC_PATTERN = Pattern.compile("<img\\s+[^>]*src=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMG_ALT_PATTERN = Pattern.compile("<img\\s+[^>]*alt=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern MISSING_IMG_PATTERN = Pattern.compile("class=\"missing-image\"[^>]*data-name=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern TR_PATTERN = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CELL_PATTERN = Pattern.compile("<t[hd][^>]*>(.*?)</t[hd]>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TH_PATTERN = Pattern.compile("<th[\\s>]", Pattern.CASE_INSENSITIVE);

    private void exportAsDocx(String content, String docName, HttpServletResponse response) throws IOException {
        XWPFDocument document = new XWPFDocument();
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        String[] lines = content.split("\n");
        StringBuilder tableBuffer = null; // 非 null 表示正在收集 table 内容

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // ====== 表格收集模式 ======
            if (tableBuffer != null) {
                tableBuffer.append(line).append("\n");
                if (line.contains("</table>")) {
                    // 表格收集完毕，解析并写入 Word
                    writeTableToDocx(document, tableBuffer.toString());
                    tableBuffer = null;
                }
                continue;
            }

            // 检测表格开始
            if (line.contains("<table")) {
                tableBuffer = new StringBuilder();
                tableBuffer.append(line).append("\n");
                // 单行内闭合的情况
                if (line.contains("</table>")) {
                    writeTableToDocx(document, tableBuffer.toString());
                    tableBuffer = null;
                }
                continue;
            }

            if (StrUtil.isBlank(line)) {
                continue;
            }

            // 1. 处理 Markdown 标题
            if (line.startsWith("#")) {
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') {
                    level++;
                }
                if (level >= 1 && level <= 6 && level < line.length() && line.charAt(level) == ' ') {
                    String text = line.substring(level + 1).trim();
                    XWPFParagraph p = document.createParagraph();
                    p.getCTP().addNewPPr().addNewOutlineLvl().setVal(BigInteger.valueOf(level - 1));
                    XWPFRun run = p.createRun();
                    run.setText(text);
                    run.setBold(true);
                    int fontSize = switch (level) {
                        case 1 -> 18;
                        case 2 -> 16;
                        case 3 -> 14;
                        default -> 13;
                    };
                    run.setFontSize(fontSize);
                    continue;
                }
            }

            // 2. 处理图片 <img src="...">
            Matcher imgMatcher = IMG_SRC_PATTERN.matcher(line);
            if (imgMatcher.find()) {
                String imgUrl = imgMatcher.group(1);
                String altText = "";
                Matcher altMatcher = IMG_ALT_PATTERN.matcher(line);
                if (altMatcher.find()) {
                    altText = altMatcher.group(1);
                }
                try {
                    byte[] imageBytes = downloadImage(httpClient, imgUrl);
                    if (imageBytes != null && imageBytes.length > 0) {
                        int pictureType = getPictureType(imgUrl);
                        XWPFParagraph imgParagraph = document.createParagraph();
                        imgParagraph.setAlignment(ParagraphAlignment.CENTER);
                        XWPFRun imgRun = imgParagraph.createRun();
                        try (InputStream is = new ByteArrayInputStream(imageBytes)) {
                            imgRun.addPicture(is, pictureType,
                                StrUtil.isNotBlank(altText) ? altText : "image",
                                org.apache.poi.util.Units.toEMU(400),
                                org.apache.poi.util.Units.toEMU(280));
                        }
                        if (StrUtil.isNotBlank(altText)) {
                            XWPFParagraph captionParagraph = document.createParagraph();
                            captionParagraph.setAlignment(ParagraphAlignment.CENTER);
                            XWPFRun captionRun = captionParagraph.createRun();
                            captionRun.setText("图：" + altText);
                            captionRun.setFontSize(9);
                            captionRun.setColor("666666");
                        }
                    }
                } catch (Exception e) {
                    log.warn("导出Word下载图片失败: {}, 错误: {}", imgUrl, e.getMessage());
                    XWPFParagraph p = document.createParagraph();
                    p.setAlignment(ParagraphAlignment.CENTER);
                    XWPFRun run = p.createRun();
                    run.setText("[图片加载失败: " + (StrUtil.isNotBlank(altText) ? altText : imgUrl) + "]");
                    run.setFontSize(9);
                    run.setColor("999999");
                }
                continue;
            }

            // 3. 处理缺失图片占位符
            Matcher missingMatcher = MISSING_IMG_PATTERN.matcher(line);
            if (missingMatcher.find()) {
                String missingName = missingMatcher.group(1);
                XWPFParagraph p = document.createParagraph();
                p.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun run = p.createRun();
                run.setText("[缺失图片: " + missingName + "]");
                run.setFontSize(9);
                run.setColor("FA8C16");
                continue;
            }

            // 4. 跳过图注容器行
            if (line.contains("class=\"chapter-image\"") || line.contains("</div>")) {
                continue;
            }
            if (line.trim().startsWith("<p") && line.contains("font-size:12px") && line.contains("text-align:center")) {
                continue;
            }

            // 5. 普通段落
            String text = stripHtmlTags(line).trim();
            if (StrUtil.isNotBlank(text)) {
                XWPFParagraph p = document.createParagraph();
                XWPFRun run = p.createRun();
                run.setText(text);
                run.setFontSize(11);
            }
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        FileUtils.setAttachmentResponseHeader(response, docName + ".docx");
        document.write(response.getOutputStream());
        document.close();
    }

    /**
     * 构建附件文本信息
     */
    private String buildAttachmentText(Long submissionId) {
        if (submissionId == null) return "";

        BizBidSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null || submission.getBidProjectId() == null) return "";

        BizBidProject project = bidProjectMapper.selectById(submission.getBidProjectId());
        if (project == null || StrUtil.isBlank(project.getAttachments())) return "";

        String[] ossIdArr = project.getAttachments().split(",");
        List<Long> ossIds = new ArrayList<>();
        for (String ossIdStr : ossIdArr) {
            try {
                ossIds.add(Long.parseLong(ossIdStr.trim()));
            } catch (NumberFormatException ignored) {}
        }
        if (ossIds.isEmpty()) return "";

        List<SysOssVo> ossList = sysOssService.listByIds(ossIds);
        if (ossList.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("**附件：**\n\n");
        for (int i = 0; i < ossList.size(); i++) {
            SysOssVo oss = ossList.get(i);
            sb.append((i + 1)).append(". ").append(oss.getOriginalName()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 将 HTML table 解析并写入 Word 表格
     */
    private void writeTableToDocx(XWPFDocument document, String tableHtml) {
        // 解析所有行
        Matcher trMatcher = TR_PATTERN.matcher(tableHtml);
        List<List<String>> rows = new ArrayList<>();
        List<Boolean> rowIsHeader = new ArrayList<>();
        int maxCols = 0;

        while (trMatcher.find()) {
            String trContent = trMatcher.group(1);
            boolean isHeader = TH_PATTERN.matcher(trContent).find();
            rowIsHeader.add(isHeader);

            Matcher cellMatcher = CELL_PATTERN.matcher(trContent);
            List<String> cells = new ArrayList<>();
            while (cellMatcher.find()) {
                // 去掉单元格内的 HTML 标签
                String cellText = stripHtmlTags(cellMatcher.group(1)).trim();
                cells.add(cellText);
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
                maxCols = Math.max(maxCols, cells.size());
            }
        }

        if (rows.isEmpty() || maxCols == 0) {
            return;
        }

        // 创建 Word 表格
        XWPFTable table = document.createTable(rows.size(), maxCols);
        table.setWidth("100%");

        for (int r = 0; r < rows.size(); r++) {
            XWPFTableRow tableRow = table.getRow(r);
            List<String> cells = rows.get(r);
            boolean isHeader = rowIsHeader.get(r);

            for (int c = 0; c < maxCols; c++) {
                XWPFTableCell cell = tableRow.getCell(c);
                String cellText = c < cells.size() ? cells.get(c) : "";
                // 清空默认段落再写入
                XWPFParagraph cellParagraph = cell.getParagraphs().get(0);
                XWPFRun run = cellParagraph.createRun();
                run.setText(cellText);
                run.setFontSize(10);
                if (isHeader) {
                    run.setBold(true);
                }
            }
        }

        // 表格后加一个空行
        document.createParagraph();
    }

    /**
     * 从 URL 下载图片字节
     */
    private byte[] downloadImage(HttpClient httpClient, String imageUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
            HttpResponse<byte[]> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200) {
                return resp.body();
            }
            log.warn("下载图片HTTP状态码异常: {} -> {}", imageUrl, resp.statusCode());
            return null;
        } catch (Exception e) {
            log.warn("下载图片异常: {} -> {}", imageUrl, e.getMessage());
            return null;
        }
    }

    /**
     * 根据 URL 后缀判断图片类型
     */
    private int getPictureType(String url) {
        String lower = url.toLowerCase();
        if (lower.contains(".png")) {
            return XWPFDocument.PICTURE_TYPE_PNG;
        } else if (lower.contains(".jpg") || lower.contains(".jpeg")) {
            return XWPFDocument.PICTURE_TYPE_JPEG;
        } else if (lower.contains(".gif")) {
            return XWPFDocument.PICTURE_TYPE_GIF;
        } else if (lower.contains(".bmp")) {
            return XWPFDocument.PICTURE_TYPE_BMP;
        }
        // 默认当 PNG
        return XWPFDocument.PICTURE_TYPE_PNG;
    }

    /**
     * 导出为 PDF（使用 Apache PDFBox 生成简单文本 PDF）
     */
    private void exportAsPdf(String content, String docName, HttpServletResponse response) throws IOException {
        org.apache.pdfbox.pdmodel.PDDocument pdfDocument = new org.apache.pdfbox.pdmodel.PDDocument();
        org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage(
            org.apache.pdfbox.pdmodel.common.PDRectangle.A4);
        pdfDocument.addPage(page);

        try (org.apache.pdfbox.pdmodel.PDPageContentStream contentStream =
                 new org.apache.pdfbox.pdmodel.PDPageContentStream(pdfDocument, page)) {

            // 使用内置字体（不支持中文，仅做框架占位；生产中替换为支持中文的字体）
            org.apache.pdfbox.pdmodel.font.PDFont font =
                new org.apache.pdfbox.pdmodel.font.PDType1Font(
                    org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA
                );

            float margin = 50;
            float pageWidth = page.getMediaBox().getWidth() - 2 * margin;
            float yStart = page.getMediaBox().getHeight() - margin;
            float lineHeight = 14;
            float y = yStart;

            contentStream.beginText();
            contentStream.setFont(font, 12);
            contentStream.newLineAtOffset(margin, y);

            String[] lines = content.split("\n");
            for (String line : lines) {
                String text = stripHtmlTags(StrUtil.blankToDefault(line, "")).trim();
                if (text.startsWith("#")) {
                    int idx = text.indexOf(' ');
                    if (idx > 0) {
                        text = text.substring(idx + 1).trim();
                    }
                }
                // PDF 不支持中文，中文字符替换为问号占位（生产中请使用支持中文的字体）
                text = text.replaceAll("[^\\x00-\\x7F]", "?");
                if (text.isEmpty()) {
                    contentStream.newLineAtOffset(0, -lineHeight);
                    continue;
                }
                // 简单截断超长行
                if (text.length() > 80) {
                    text = text.substring(0, 80) + "...";
                }
                contentStream.showText(text);
                contentStream.newLineAtOffset(0, -lineHeight);
                y -= lineHeight;
                // 翻页（简单处理，超出区域截断）
                if (y < margin) {
                    break;
                }
            }
            contentStream.endText();
        }

        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        FileUtils.setAttachmentResponseHeader(response, docName + ".pdf");
        pdfDocument.save(response.getOutputStream());
        pdfDocument.close();
    }

    /**
     * 简单去除 HTML 标签
     */
    private String stripHtmlTags(String html) {
        if (StrUtil.isBlank(html)) {
            return "";
        }
        return html.replaceAll("<[^>]*>", "")
            .replaceAll("&nbsp;", " ")
            .replaceAll("&amp;", "&")
            .replaceAll("&lt;", "<")
            .replaceAll("&gt;", ">")
            .replaceAll("&quot;", "\"");
    }

    /**
     * 文档类型中文标签
     */
    private String getDocumentTypeLabel(String type) {
        if (type == null) return "标书";
        return switch (type) {
            case "technical" -> "技术标";
            case "commercial" -> "商务标";
            case "complete" -> "完整标书";
            default -> "标书";
        };
    }
}

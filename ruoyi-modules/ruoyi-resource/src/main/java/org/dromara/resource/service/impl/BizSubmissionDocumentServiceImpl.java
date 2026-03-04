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
import org.dromara.resource.domain.BizDocumentConfig;
import org.dromara.resource.domain.BizSubmissionChapter;
import org.dromara.resource.domain.BizSubmissionDocument;
import org.dromara.resource.domain.vo.BizSubmissionDocumentVo;
import org.dromara.resource.mapper.BizDocumentConfigMapper;
import org.dromara.resource.mapper.BizSubmissionChapterMapper;
import org.dromara.resource.mapper.BizSubmissionDocumentMapper;
import org.dromara.resource.service.IBizSubmissionDocumentService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

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

    @Override
    public List<BizSubmissionDocumentVo> listLatestBySubmissionId(Long submissionId) {
        return baseMapper.selectLatestBySubmissionId(submissionId);
    }

    @Override
    public List<BizSubmissionDocumentVo> listVersionsByConfigId(Long documentConfigId) {
        return baseMapper.selectVersionsByConfigId(documentConfigId);
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
        lqw.orderByAsc(BizSubmissionChapter::getSortOrder);
        List<BizSubmissionChapter> chapters = chapterMapper.selectList(lqw);

        String markdownContent = buildMarkdownContent(chapters);

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
    }

    // ----------------------------- 私有方法 -----------------------------

    /**
     * 将章节列表合并为 Markdown 字符串
     */
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
    private void exportAsDocx(String content, String docName, HttpServletResponse response) throws IOException {
        XWPFDocument document = new XWPFDocument();

        // 将 Markdown 逐行写入 Word（简单处理：按行分段，#开头当标题）
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (StrUtil.isBlank(line)) {
                continue;
            }
            if (line.startsWith("# ")) {
                XWPFParagraph p = document.createParagraph();
                p.setStyle("Heading1");
                XWPFRun run = p.createRun();
                run.setText(line.substring(2).trim());
                run.setBold(true);
                run.setFontSize(18);
            } else if (line.startsWith("## ")) {
                XWPFParagraph p = document.createParagraph();
                p.setStyle("Heading2");
                XWPFRun run = p.createRun();
                run.setText(line.substring(3).trim());
                run.setBold(true);
                run.setFontSize(16);
            } else if (line.startsWith("### ")) {
                XWPFParagraph p = document.createParagraph();
                p.setStyle("Heading3");
                XWPFRun run = p.createRun();
                run.setText(line.substring(4).trim());
                run.setBold(true);
                run.setFontSize(14);
            } else if (line.startsWith("#### ") || line.startsWith("##### ") || line.startsWith("###### ")) {
                int level = line.indexOf(' ');
                XWPFParagraph p = document.createParagraph();
                XWPFRun run = p.createRun();
                run.setText(line.substring(level + 1).trim());
                run.setBold(true);
                run.setFontSize(13);
            } else {
                // 普通段落 - 处理 HTML 内容（去除标签后输出）
                XWPFParagraph p = document.createParagraph();
                XWPFRun run = p.createRun();
                run.setText(stripHtmlTags(line));
                run.setFontSize(11);
            }
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        FileUtils.setAttachmentResponseHeader(response, docName + ".docx");
        document.write(response.getOutputStream());
        document.close();
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

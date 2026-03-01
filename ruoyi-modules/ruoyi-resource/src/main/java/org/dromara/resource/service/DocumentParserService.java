package org.dromara.resource.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 文档解析服务
 * 支持 PDF 和 Word 文档的文本提取
 *
 * @author ruoyi
 */
@Slf4j
@Service
public class DocumentParserService {

    /**
     * 解析文档内容
     *
     * @param file 上传的文件
     * @return 解析后的文本内容
     */
    public String parseDocument(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String extension = getFileExtension(filename).toLowerCase();

        return switch (extension) {
            case "pdf" -> parsePdf(file.getInputStream());
            case "doc", "docx" -> parseWord(file.getInputStream());
            default -> throw new IllegalArgumentException("不支持的文件格式: " + extension);
        };
    }

    /**
     * 解析 PDF 文件
     */
    private String parsePdf(InputStream inputStream) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.info("成功解析 PDF 文档，提取文本长度: {}", text.length());
            return text;
        }
    }

    /**
     * 解析 Word 文件
     */
    private String parseWord(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder content = new StringBuilder();
            List<XWPFParagraph> paragraphs = document.getParagraphs();

            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph.getText();
                if (text != null && !text.trim().isEmpty()) {
                    content.append(text).append("\n");
                }
            }

            String result = content.toString();
            log.info("成功解析 Word 文档，提取文本长度: {}", result.length());
            return result;
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }

    /**
     * 验证文件类型
     */
    public boolean isValidFileType(String filename) {
        if (filename == null) {
            return false;
        }
        String extension = getFileExtension(filename).toLowerCase();
        return extension.equals("pdf") || extension.equals("doc") || extension.equals("docx");
    }
}

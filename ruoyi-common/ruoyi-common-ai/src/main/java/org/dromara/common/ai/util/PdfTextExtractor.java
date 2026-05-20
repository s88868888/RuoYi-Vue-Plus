package org.dromara.common.ai.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 文本提取与渲染工具
 * <p>
 * 用于审核场景下区分"打印件 PDF"（带文本层，直接抽文本）和"扫描件 PDF"（无文本层，需OCR）：
 * - {@link #hasTextLayer(File)} 探测有没有文本层
 * - {@link #extractText(File)}  对打印件直接抽文本
 * - {@link #renderPagesToPng(File)} 对扫描件逐页转 PNG，交给视觉模型 OCR
 * @author Linson
 */
@Slf4j
public class PdfTextExtractor {

    /** 探测的前 N 页文本字符数低于此阈值时，判定为扫描件 */
    private static final int TEXT_LAYER_THRESHOLD = 50;
    /** 探测时只抽前几页（足以判断有无文本层，避免大文件全量解析） */
    private static final int PROBE_PAGES = 2;
    /** OCR 渲染 DPI（144 在识别率和大小间取平衡，太低识不出小字，太高图过大） */
    private static final int RENDER_DPI = 144;

    private PdfTextExtractor() {}

    /**
     * 探测 PDF 是否包含文本层：
     * 抽前 {@value #PROBE_PAGES} 页文本，trim 后字符数 ≥ {@value #TEXT_LAYER_THRESHOLD} 即认为是打印件。
     * 解析异常时按扫描件处理（保守策略，让 OCR 兜底总好过抛错）。
     */
    public static boolean hasTextLayer(File pdfFile) {
        try (PDDocument doc = Loader.loadPDF(pdfFile)) {
            int endPage = Math.min(PROBE_PAGES, doc.getNumberOfPages());
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(endPage);
            String text = stripper.getText(doc);
            int len = text == null ? 0 : text.trim().length();
            boolean hasText = len >= TEXT_LAYER_THRESHOLD;
            log.info("[PdfTextExtractor] {} 前{}页抽出字符数={}, 判定={}",
                pdfFile.getName(), endPage, len, hasText ? "打印件(有文本层)" : "扫描件(无文本层)");
            return hasText;
        } catch (Exception e) {
            log.warn("[PdfTextExtractor] {} 文本层探测失败，按扫描件处理: {}",
                pdfFile.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * 抽取整个 PDF 的纯文本（仅对打印件使用，扫描件会返回空字符串）
     */
    public static String extractText(File pdfFile) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            return text == null ? "" : text;
        }
    }

    /**
     * 把 PDF 每一页渲染成 PNG 字节数组，按页码顺序返回。
     * 用于扫描件 OCR：逐页喂给 qwen-vl 视觉模型。
     */
    public static List<byte[]> renderPagesToPng(File pdfFile) throws Exception {
        List<byte[]> images = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdfFile)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int pageCount = doc.getNumberOfPages();
            for (int i = 0; i < pageCount; i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, RENDER_DPI);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "png", baos);
                images.add(baos.toByteArray());
                log.info("[PdfTextExtractor] {} 第{}/{}页渲染完成 ({} bytes)",
                    pdfFile.getName(), i + 1, pageCount, baos.size());
            }
        }
        return images;
    }
}

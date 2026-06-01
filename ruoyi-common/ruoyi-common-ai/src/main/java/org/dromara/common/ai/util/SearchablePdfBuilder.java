package org.dromara.common.ai.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.dromara.common.ai.ocr.OcrProvider;
import org.dromara.common.ai.ocr.dto.OcrPageResult;
import org.dromara.common.ai.ocr.dto.OcrTextBlock;
import org.springframework.core.io.ByteArrayResource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Searchable PDF 构建器
 * <p>
 * 把扫描件 PDF 转成"看着是图、选/搜是字"的 searchable PDF:
 * <ol>
 *   <li>用 {@link PdfTextExtractor#renderPagesToPng} 把每页渲染成 PNG (DPI=144)</li>
 *   <li>对每页 PNG 调 OCR 拿到结构化结果(文字 + 4点坐标)</li>
 *   <li>在原 PDF 内容流之上,以 rendering mode 3(不可见)写入 OCR 文字到对应坐标</li>
 * </ol>
 * 产出的 PDF:视觉与原扫描件 100% 一致,但 pdfjs textLayer 会有完整文字层,
 * 城更系统 FileDiffViewer 可直接当打印版加载,字符级 diff 高亮自动可用。
 * <p>
 * 坐标换算:OCR 像素(原图 DPI=144) → PDF 用户坐标(point=72/inch),
 * 公式 {@code pt = pixel * 72 / DPI}, Y 翻转 {@code pdf_y = page_h - pixel_y * 72 / DPI}.
 *
 * @author Linson
 */
@Slf4j
public final class SearchablePdfBuilder {

    /** 与 PdfTextExtractor.RENDER_DPI 保持一致;改这里要同步改那边 */
    private static final int OCR_DPI = 144;

    /** 字号上下限,防止极端 OCR 框算出来的字号过大/过小 */
    private static final float MIN_FONT_SIZE = 4f;
    private static final float MAX_FONT_SIZE = 120f;

    private SearchablePdfBuilder() {}

    /**
     * 构建 searchable PDF,直接返回字节数组
     *
     * @param sourcePdf   源 PDF 文件
     * @param ocrProvider OCR 提供者(必须支持 ocrStructured)
     * @return searchable PDF byte[]
     */
    public static byte[] build(File sourcePdf, OcrProvider ocrProvider) throws IOException {
        return build(sourcePdf, ocrProvider, null);
    }

    /**
     * 构建 searchable PDF,可选传入 OCR config
     */
    public static byte[] build(File sourcePdf, OcrProvider ocrProvider,
                               org.dromara.common.ai.dto.AiModelConfigDto ocrConfig) throws IOException {
        long start = System.currentTimeMillis();
        // 1. 渲染原 PDF 每页成 PNG(用与坐标系一致的 144 DPI)
        List<byte[]> pageImages;
        try {
            pageImages = PdfTextExtractor.renderPagesToPng(sourcePdf);
        } catch (Exception e) {
            throw new IOException("渲染 PDF 页面失败: " + e.getMessage(), e);
        }
        if (pageImages == null || pageImages.isEmpty()) {
            throw new IOException("PDF 没有可渲染的页面: " + sourcePdf.getName());
        }

        try (PDDocument doc = Loader.loadPDF(sourcePdf)) {
            int pdfPages = doc.getNumberOfPages();
            if (pdfPages != pageImages.size()) {
                log.warn("[SearchablePdfBuilder] PDF 页数({})与渲染图片数({})不一致,按较小值处理",
                    pdfPages, pageImages.size());
            }
            int total = Math.min(pdfPages, pageImages.size());
            PDType0Font font = CjkFontLoader.load(doc);

            for (int i = 0; i < total; i++) {
                long t0 = System.currentTimeMillis();
                byte[] pngBytes = pageImages.get(i);
                OcrPageResult page = ocrProvider.ocrStructured(
                    new ByteArrayResource(pngBytes), ocrConfig);
                if (page == null || page.getBlocks() == null || page.getBlocks().isEmpty()) {
                    log.info("[SearchablePdfBuilder] 第{}页 OCR 无文本,跳过", i + 1);
                    continue;
                }
                writeInvisibleTextLayer(doc, doc.getPage(i), pngBytes, page, font);
                log.info("[SearchablePdfBuilder] 第{}/{}页完成: {} blocks, {} ms",
                    i + 1, total, page.getBlocks().size(), System.currentTimeMillis() - t0);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            log.info("[SearchablePdfBuilder] {} 全部完成: {} 页, 总耗时 {} ms, {} bytes",
                sourcePdf.getName(), total, System.currentTimeMillis() - start, out.size());
            return out.toByteArray();
        }
    }

    /**
     * 在指定页上写入不可见文字层。
     * <p>
     * 关键:OCR 坐标基于渲染图(像素),需用渲染图实际尺寸推回 PDF 坐标系——
     * 不要假设 DPI 就是 OCR_DPI,因为 PDFBox 渲染时可能根据 PDF mediabox 调整。
     * 用 imageWidth/imageHeight 与 PDF page 宽高之比作为 scale 最稳。
     */
    private static void writeInvisibleTextLayer(PDDocument doc, PDPage page,
                                                byte[] pngBytes, OcrPageResult ocrResult,
                                                PDType0Font font) throws IOException {
        // PDF 用户坐标(point)
        PDRectangle box = page.getMediaBox();
        float pageW = box.getWidth();
        float pageH = box.getHeight();

        // OCR 输入图实际尺寸(像素)
        int imgW = ocrResult.getImageWidth();
        int imgH = ocrResult.getImageHeight();
        if (imgW <= 0 || imgH <= 0) {
            // 兜底:从 PNG 字节直接读
            int[] wh = readPngSize(pngBytes);
            imgW = wh[0];
            imgH = wh[1];
        }
        if (imgW <= 0 || imgH <= 0) {
            log.warn("无法获得渲染图尺寸,本页跳过");
            return;
        }
        // 像素 → PDF point 缩放比例(直接用图与页面比,自动包含 DPI 偏差)
        float sx = pageW / (float) imgW;
        float sy = pageH / (float) imgH;

        try (PDPageContentStream cs = new PDPageContentStream(
                doc, page, AppendMode.APPEND, true, true)) {
            cs.beginText();
            cs.setRenderingMode(RenderingMode.NEITHER); // mode 3:不可见(可选可搜)

            for (OcrTextBlock blk : ocrResult.getBlocks()) {
                writeOneBlock(cs, font, blk, sx, sy, pageH);
            }
            cs.endText();
        }
    }

    /**
     * 写单个文本块。每块设置 textMatrix 到对应位置,字号 = poly 高度,
     * horizontalScaling 调整使字符总宽 ≈ poly 宽度。
     */
    private static void writeOneBlock(PDPageContentStream cs, PDType0Font font,
                                      OcrTextBlock blk, float sx, float sy, float pageH)
            throws IOException {
        if (blk.getText() == null || blk.getText().isBlank()) return;
        if (blk.getPoly() == null || blk.getPoly().size() < 4) return;

        // 取 bbox(像素)
        int[] box = blk.bbox();
        int pxX = box[0], pxY = box[1], pxW = box[2], pxH = box[3];
        if (pxW <= 0 || pxH <= 0) return;

        // 字号:bbox 高度按 sy 缩放后 ≈ 字体高度。PDFBox 字号是 em 大小,
        // 中文方块字 ≈ 1em,所以字号约等于像素高 * sy
        float fontSize = pxH * sy;
        if (fontSize < MIN_FONT_SIZE) fontSize = MIN_FONT_SIZE;
        if (fontSize > MAX_FONT_SIZE) fontSize = MAX_FONT_SIZE;

        // 目标宽度(PDF point)
        float targetW = pxW * sx;
        // 字符串实际宽(默认水平缩放 100%)
        String text = sanitize(blk.getText(), font);
        if (text.isEmpty()) return;
        float naturalW;
        try {
            naturalW = font.getStringWidth(text) / 1000f * fontSize;
        } catch (IOException | IllegalArgumentException e) {
            // getStringWidth 偶尔遇到字体不支持的字符抛错;退到只取首字符
            log.warn("[SearchablePdfBuilder] 计算字宽失败,跳过: '{}', err={}",
                text.length() > 20 ? text.substring(0, 20) : text, e.getMessage());
            return;
        }
        // 水平缩放百分比让总宽 = OCR 检测框宽
        float hScale = naturalW > 0.01f ? (targetW / naturalW) * 100f : 100f;
        // 限制极端缩放(过窄 OCR 框)
        if (hScale < 10f) hScale = 10f;
        if (hScale > 500f) hScale = 500f;

        // PDF 坐标:Y 翻转(box[1]是顶部像素,PDF 原点在左下)。
        // baseline 在 bbox 底部 → pdfY = pageH - (pxY + pxH) * sy
        float pdfX = pxX * sx;
        float pdfY = pageH - (pxY + pxH) * sy;

        cs.setFont(font, fontSize);
        cs.setHorizontalScaling(hScale);
        cs.setTextMatrix(org.apache.pdfbox.util.Matrix.getTranslateInstance(pdfX, pdfY));
        cs.showText(text);
    }

    /** 过滤字体不支持的字符(主要是 emoji / 私有码点),否则 showText 会抛 */
    private static String sanitize(String s, PDType0Font font) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            // 控制字符
            if (cp < 0x20 && cp != '\t') continue;
            // ZWSP / 不可见
            if (cp == 0x200B || cp == 0x200C || cp == 0x200D || cp == 0xFEFF) continue;
            try {
                font.encode(new String(Character.toChars(cp)));
                sb.appendCodePoint(cp);
            } catch (Exception ignore) {
                // 字体不支持,跳过;不要替换成 ? 否则会污染搜索结果
            }
        }
        return sb.toString();
    }

    private static int[] readPngSize(byte[] png) {
        if (png == null || png.length < 24) return new int[]{0, 0};
        if (png[0] == (byte) 0x89 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G') {
            int w = ((png[16] & 0xff) << 24) | ((png[17] & 0xff) << 16)
                | ((png[18] & 0xff) << 8) | (png[19] & 0xff);
            int h = ((png[20] & 0xff) << 24) | ((png[21] & 0xff) << 16)
                | ((png[22] & 0xff) << 8) | (png[23] & 0xff);
            return new int[]{w, h};
        }
        return new int[]{0, 0};
    }
}

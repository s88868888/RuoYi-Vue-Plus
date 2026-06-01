package org.dromara.common.ai.ocr.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OCR 结构化结果(单页)
 * <p>
 * 包含原图像素尺寸 + 文本块列表,用于:
 * 1. SearchablePdfBuilder 反推 PDF 用户坐标系
 * 2. 前端坐标可视化(虽然实际接入用 searchable PDF + pdfjs textLayer,这里保留作 fallback)
 *
 * @author Linson
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OcrPageResult {

    /** OCR 输入图(PaddleX inputImage)宽度,像素 */
    private int imageWidth;

    /** OCR 输入图高度,像素 */
    private int imageHeight;

    /** 文本块列表 */
    private List<OcrTextBlock> blocks;

    /** 该页耗时 ms */
    private long elapsedMs;
}

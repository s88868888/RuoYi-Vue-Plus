package org.dromara.common.ai.ocr.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OCR 单文本块结构化结果
 * <p>
 * 一个 block 对应 PaddleOCR 检测到的一行/一个文本块,带 4 点多边形坐标和置信度。
 * 坐标系是 PaddleX 入图(inputImage)的像素坐标——左上原点,Y 向下。
 * <p>
 * 用于 SearchablePdfBuilder 把每个 block 写回 PDF 不可见文字层。
 *
 * @author Linson
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OcrTextBlock {

    /** 识别文字 */
    private String text;

    /** 置信度 0~1 */
    private Double score;

    /**
     * 4 点多边形坐标 [[x,y]*4],按顺时针: 左上、右上、右下、左下。
     * 即 PaddleOCR rec_polys[i]。
     */
    private List<int[]> poly;

    /** bounding box (x, y, width, height),从 poly 算出,方便上层使用 */
    public int[] bbox() {
        if (poly == null || poly.isEmpty()) return new int[]{0, 0, 0, 0};
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int[] p : poly) {
            if (p == null || p.length < 2) continue;
            if (p[0] < minX) minX = p[0];
            if (p[0] > maxX) maxX = p[0];
            if (p[1] < minY) minY = p[1];
            if (p[1] > maxY) maxY = p[1];
        }
        return new int[]{minX, minY, maxX - minX, maxY - minY};
    }
}

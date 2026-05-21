package org.dromara.common.ai.ocr;

import org.springframework.core.io.Resource;

/**
 * OCR Provider 抽象
 * <p>
 * 把"图片 → 文字"这件事抽象出来，让审核流程可以在不同 OCR 实现间切换：
 * - {@link QwenVlOcrProvider}：云端，识别精度最高，含印章/手写
 * - {@link PaddleOcrProvider}：本地，PP-OCRv4，印章/手写一般，速度快
 * <p>
 * 由 {@link OcrProviderFactory} 根据配置 {@code review.ocr.provider} 选取。
 */
public interface OcrProvider {

    /** Provider 标识，与配置项 review.ocr.provider 对齐 */
    String getName();

    /**
     * 对单张图片做 OCR 识别
     *
     * @param imageResource 图片资源（PNG / JPG）
     * @return 提取到的纯文本（按版面顺序，可能含换行）
     */
    String ocr(Resource imageResource);
}

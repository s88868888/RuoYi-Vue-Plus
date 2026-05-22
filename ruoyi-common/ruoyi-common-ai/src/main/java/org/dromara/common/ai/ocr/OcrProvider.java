package org.dromara.common.ai.ocr;

import org.dromara.common.ai.dto.AiModelConfigDto;
import org.springframework.core.io.Resource;

/**
 * OCR Provider 抽象
 * <p>
 * 把"图片 → 文字"这件事抽象出来，让审核流程可以在不同 OCR 实现间切换：
 * - {@link QwenVlOcrProvider}：云端，识别精度最高，含印章/手写
 * - {@link PaddleOcrProvider}：本地，PP-OCRv5，印章/手写一般，速度快
 * <p>
 * 由 {@link OcrProviderFactory} 根据 review_model_config (purpose=ocr) 表选取，
 * 表中查不到时回退到 yml 配置 {@code review.ocr.provider}。
 */
public interface OcrProvider {

    /** Provider 标识，与 review_model_config.provider / yml review.ocr.provider 对齐 */
    String getName();

    /**
     * 用 yml 默认参数做 OCR（兼容旧调用方）
     */
    String ocr(Resource imageResource);

    /**
     * 用指定配置做 OCR（DB 配置驱动入口）
     * <p>
     * config 的字段语义因 provider 不同：
     * - paddleocr: baseUrl=PaddleX serve URL, timeoutMs=超时, extraOptions.confidenceThreshold=阈值
     * - qwen-vl-ocr: 走 AiChatService.chatWithImage，模型ID/温度从 config 取
     *
     * @param imageResource 图片资源
     * @param config        运行时配置；为 null 等价于 ocr(imageResource)
     */
    default String ocr(Resource imageResource, AiModelConfigDto config) {
        return ocr(imageResource);
    }
}

package org.dromara.common.ai.ocr;

import org.dromara.common.ai.dto.AiModelConfigDto;

/**
 * OCR 配置解析器（跨模块抽象）
 * <p>
 * 由 ruoyi-review 模块实现，负责把"配置 ID"或"全局兜底"翻译成 {@link AiModelConfigDto}。
 * common-ai 不依赖 ruoyi-review。
 */
public interface OcrConfigResolver {

    /** 取全局兜底 OCR 配置（purpose=ocr 中最早 enabled 的一条），找不到返回 null */
    AiModelConfigDto getActiveOcrConfig();

    /**
     * 按显式 ID 取 OCR 配置；id 为 null 或查不到时回退 {@link #getActiveOcrConfig()}
     * <p>
     * 用于 ReviewAgent 把模板的 ocrConfigId 透传过来，让每条任务可以走不同 OCR 通道。
     */
    AiModelConfigDto getOcrConfigById(Long id);
}

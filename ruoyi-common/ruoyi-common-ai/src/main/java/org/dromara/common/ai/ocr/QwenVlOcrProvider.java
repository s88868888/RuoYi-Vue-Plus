package org.dromara.common.ai.ocr;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.dto.AiModelConfigDto;
import org.dromara.common.ai.service.AiChatService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * 云端 qwen-vl OCR 实现
 * <p>
 * 包装 {@link AiChatService#ocrImage(Resource)}（默认模型 qwen3-vl-plus）；
 * DB 配置 provider=qwen-vl-ocr 时此实现生效，但当前 chatWithImage 实现已固定模型，
 * 这里 config 主要用于日志追溯。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QwenVlOcrProvider implements OcrProvider {

    public static final String NAME = "qwen-vl-ocr";

    private final AiChatService aiChatService;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String ocr(Resource imageResource) {
        return ocr(imageResource, null);
    }

    @Override
    public String ocr(Resource imageResource, AiModelConfigDto config) {
        long start = System.currentTimeMillis();
        String text = aiChatService.ocrImage(imageResource);
        log.info("[QwenVlOcrProvider] OCR 完成:  字符, {} ms, model={}",
            text == null ? 0 : text.length(),
            System.currentTimeMillis() - start,
            config == null ? "qwen3-vl-plus(默认)" : config.getModelName());
        return text == null ? "" : text;
    }
}

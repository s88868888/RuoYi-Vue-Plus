package org.dromara.common.ai.ocr;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.service.AiChatService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * 云端 qwen-vl OCR 实现
 * <p>
 * 包装 {@link AiChatService#ocrImage(Resource)}，识别精度最高（含印章/手写/模糊字），
 * 但单页 3~8 秒、需要 DashScope API Key、协议像素会上云。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QwenVlOcrProvider implements OcrProvider {

    public static final String NAME = "qwen-vl";

    private final AiChatService aiChatService;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String ocr(Resource imageResource) {
        long start = System.currentTimeMillis();
        String text = aiChatService.ocrImage(imageResource);
        log.info("[QwenVlOcrProvider] OCR 完成: {} 字符, {} ms",
            text == null ? 0 : text.length(), System.currentTimeMillis() - start);
        return text == null ? "" : text;
    }
}

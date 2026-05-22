package org.dromara.review.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.dto.AiModelConfigDto;
import org.dromara.common.ai.ocr.OcrConfigResolver;
import org.dromara.review.service.IReviewModelConfigService;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewOcrConfigResolver implements OcrConfigResolver {

    private final IReviewModelConfigService modelConfigService;

    @Override
    public AiModelConfigDto getActiveOcrConfig() {
        try {
            return modelConfigService.getDefaultByPurpose("ocr");
        } catch (Exception e) {
            log.warn("[ReviewOcrConfigResolver] 加载默认 OCR 配置失败，回退 yml: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public AiModelConfigDto getOcrConfigById(Long id) {
        if (id == null) return getActiveOcrConfig();
        try {
            AiModelConfigDto cfg = modelConfigService.getEnabledDto(id);
            if (!"ocr".equalsIgnoreCase(cfg.getPurpose())) {
                log.warn("[ReviewOcrConfigResolver] 配置 id={} purpose={} 非 ocr，回退默认", id, cfg.getPurpose());
                return getActiveOcrConfig();
            }
            return cfg;
        } catch (Exception e) {
            log.warn("[ReviewOcrConfigResolver] 取 OCR 配置 id={} 失败，回退默认: {}", id, e.getMessage());
            return getActiveOcrConfig();
        }
    }
}

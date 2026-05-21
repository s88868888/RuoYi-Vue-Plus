package org.dromara.common.ai.ocr;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * OCR Provider 工厂
 * <p>
 * 根据 {@code review.ocr.provider} 配置项从已注册的 Provider Bean 列表中挑选。
 * 启动时不存在匹配 Provider 时会日志告警并降级到第一个可用实现，避免审核流程整体崩溃。
 */
@Slf4j
@Component
public class OcrProviderFactory {

    private final List<OcrProvider> providers;
    private final OcrProperties properties;
    private OcrProvider current;

    @Autowired
    public OcrProviderFactory(List<OcrProvider> providers, OcrProperties properties) {
        this.providers = providers;
        this.properties = properties;
        this.current = resolve();
    }

    /** 当前生效的 Provider */
    public OcrProvider get() {
        return current;
    }

    private OcrProvider resolve() {
        String want = properties.getProvider();
        for (OcrProvider p : providers) {
            if (p.getName().equalsIgnoreCase(want)) {
                log.info("[OcrProviderFactory] 启用 OCR Provider: {}", want);
                return p;
            }
        }
        OcrProvider fallback = providers.isEmpty() ? null : providers.get(0);
        log.warn("[OcrProviderFactory] 未找到 OCR Provider: {}, 已降级到 {}",
            want, fallback == null ? "无" : fallback.getName());
        return fallback;
    }
}

package org.dromara.common.ai.ocr;

import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.dto.AiModelConfigDto;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * OCR Provider 工厂
 * <p>
 * 选取顺序：
 * 1. {@link OcrConfigResolver} 提供 DB 配置（review_model_config purpose=ocr，is_default 优先）
 * 2. yml {@code review.ocr.provider} 兜底
 * 3. 还找不到就降级到第一个可用 Provider
 * <p>
 * 不缓存"当前 Provider"，每次调用 {@link #ocr(Resource)} 时实时解析，
 * 这样运营在 DB 层切换 OCR 通道立即生效，无需重启。
 */
@Slf4j
@Component
public class OcrProviderFactory {

    private final List<OcrProvider> providers;
    private final OcrProperties properties;
    /** review 模块提供，common-ai 单独跑（如未来非 review 用例）会是 null */
    private final OcrConfigResolver configResolver;

    @Autowired
    public OcrProviderFactory(List<OcrProvider> providers,
                              OcrProperties properties,
                              ObjectProvider<OcrConfigResolver> configResolverProvider) {
        this.providers = providers;
        this.properties = properties;
        this.configResolver = configResolverProvider.getIfAvailable();
        if (configResolver == null) {
            log.info("[OcrProviderFactory] 未注入 OcrConfigResolver，将使用 yml 配置 (provider={})",
                properties.getProvider());
        } else {
            log.info("[OcrProviderFactory] 已启用 DB 驱动配置（review_model_config purpose=ocr），yml 作为兜底");
        }
    }

    /**
     * 用当前生效配置做 OCR（DB 优先 + 热切换）
     */
    public String ocr(Resource imageResource) {
        AiModelConfigDto config = resolveConfig();
        OcrProvider provider = resolveProvider(config);
        return provider.ocr(imageResource, config);
    }

    /**
     * 按指定 OCR 配置 ID 做 OCR（来自模板的 ocrConfigId）。
     * id 为 null 或查不到时退化成 {@link #ocr(Resource)} 走全局兜底。
     */
    public String ocrWithConfig(Resource imageResource, Long configId) {
        AiModelConfigDto config = configId != null && configResolver != null
            ? configResolver.getOcrConfigById(configId)
            : resolveConfig();
        OcrProvider provider = resolveProvider(config);
        return provider.ocr(imageResource, config);
    }

    /** 取当前 OCR Provider 名（用于日志）；指定 configId 时取该配置的 provider */
    public String currentProviderName(Long configId) {
        AiModelConfigDto config = configId != null && configResolver != null
            ? configResolver.getOcrConfigById(configId)
            : resolveConfig();
        return resolveProvider(config).getName();
    }

    public String currentProviderName() {
        return currentProviderName(null);
    }

    /** 兼容旧调用：直接拿 Provider（按 yml 配置选） */
    public OcrProvider get() {
        return resolveProvider(null);
    }

    /** 取 DB 配置；查不到返回 null 走 yml 路径 */
    private AiModelConfigDto resolveConfig() {
        if (configResolver == null) return null;
        try {
            return configResolver.getActiveOcrConfig();
        } catch (Exception e) {
            log.warn("[OcrProviderFactory] 加载 DB OCR 配置失败，回退 yml: {}", e.getMessage());
            return null;
        }
    }

    private OcrProvider resolveProvider(AiModelConfigDto config) {
        String want = config != null && config.getProvider() != null
            ? config.getProvider()
            : properties.getProvider();
        for (OcrProvider p : providers) {
            if (p.getName().equalsIgnoreCase(want)) {
                return p;
            }
        }
        OcrProvider fallback = providers.isEmpty() ? null : providers.get(0);
        log.warn("[OcrProviderFactory] 未找到 OCR Provider: {}, 已降级到 {}",
            want, fallback == null ? "无" : fallback.getName());
        if (fallback == null) {
            throw new IllegalStateException("没有可用的 OCR Provider，请检查 Bean 注册");
        }
        return fallback;
    }
}

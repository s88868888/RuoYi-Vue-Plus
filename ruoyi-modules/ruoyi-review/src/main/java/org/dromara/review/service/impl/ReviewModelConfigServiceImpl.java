package org.dromara.review.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.dto.AiModelConfigDto;
import org.dromara.common.ai.service.AiChatService;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.review.domain.ReviewModelConfig;
import org.dromara.review.domain.bo.ReviewModelConfigBo;
import org.dromara.review.domain.vo.ReviewModelConfigTestVo;
import org.dromara.review.domain.vo.ReviewModelConfigVo;
import org.dromara.review.mapper.ReviewModelConfigMapper;
import org.dromara.review.service.IReviewModelConfigService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * AI 模型配置 Service 实现
 *
 * @author Linson
 * @date 2026-05-22
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ReviewModelConfigServiceImpl implements IReviewModelConfigService {

    private final ReviewModelConfigMapper baseMapper;
    private final AiChatService aiChatService;

    @Override
    public ReviewModelConfigVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public List<ReviewModelConfigVo> queryList(String provider, String enabled, String purpose) {
        LambdaQueryWrapper<ReviewModelConfig> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(provider), ReviewModelConfig::getProvider, provider);
        lqw.eq(StringUtils.isNotBlank(enabled), ReviewModelConfig::getEnabled, enabled);
        lqw.eq(StringUtils.isNotBlank(purpose), ReviewModelConfig::getPurpose, purpose);
        lqw.orderByAsc(ReviewModelConfig::getId);
        return baseMapper.selectVoList(lqw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(ReviewModelConfigBo bo) {
        checkCodeUnique(bo.getCode(), null);
        ReviewModelConfig add = MapstructUtils.convert(bo, ReviewModelConfig.class);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag && add != null) {
            bo.setId(add.getId());
        }
        return flag;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(ReviewModelConfigBo bo) {
        checkCodeUnique(bo.getCode(), bo.getId());
        ReviewModelConfig update = MapstructUtils.convert(bo, ReviewModelConfig.class);
        boolean ok = baseMapper.updateById(update) > 0;
        if (ok) {
            // 编辑后立即生效：让 AiChatService 下次取最新参数重建实例
            aiChatService.evictModelCache(bo.getId());
        }
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        boolean ok = baseMapper.deleteByIds(ids) > 0;
        if (ok) {
            for (Long id : ids) {
                aiChatService.evictModelCache(id);
            }
        }
        return ok;
    }

    @Override
    public AiModelConfigDto getEnabledDto(Long id) {
        ReviewModelConfig c = baseMapper.selectById(id);
        if (c == null) {
            throw new RuntimeException("AI模型配置不存在: id=" + id);
        }
        if (!"1".equals(c.getEnabled())) {
            throw new RuntimeException("AI模型配置已禁用: " + c.getName() + " (" + c.getCode() + ")");
        }
        return toDto(c);
    }

    @Override
    public AiModelConfigDto getDefaultByPurpose(String purpose) {
        if (StringUtils.isBlank(purpose)) return null;
        // 不再依赖 is_default 字段（已删除），按 enabled=1 + 最早创建（id 升序）
        // 取第一条作为该 purpose 的"全局兜底"。模板自己挂的 ocrConfigId 优先级更高。
        LambdaQueryWrapper<ReviewModelConfig> lqw = Wrappers.<ReviewModelConfig>lambdaQuery()
            .eq(ReviewModelConfig::getPurpose, purpose)
            .eq(ReviewModelConfig::getEnabled, "1")
            .orderByAsc(ReviewModelConfig::getId)
            .last("LIMIT 1");
        ReviewModelConfig c = baseMapper.selectOne(lqw);
        return c == null ? null : toDto(c);
    }

    private AiModelConfigDto toDto(ReviewModelConfig c) {
        Map<String, Object> opts = null;
        if (StringUtils.isNotBlank(c.getExtraOptions())) {
            try {
                opts = JSON.parseObject(c.getExtraOptions());
            } catch (Exception e) {
                log.warn("[ReviewModelConfig] extraOptions JSON 解析失败 id={}, raw={}, err={}",
                    c.getId(), c.getExtraOptions(), e.getMessage());
            }
        }
        return AiModelConfigDto.builder()
            .id(c.getId())
            .code(c.getCode())
            .provider(c.getProvider())
            .modelName(c.getModelName())
            .baseUrl(c.getBaseUrl())
            .apiKey(c.getApiKey())
            .numCtx(c.getNumCtx())
            .numPredict(c.getNumPredict())
            .maxTokens(c.getMaxTokens())
            .temperature(c.getTemperature())
            .topP(c.getTopP())
            .timeoutMs(c.getTimeoutMs())
            .kvCacheType(c.getKvCacheType())
            .purpose(c.getPurpose())
            .extraOptions(opts)
            .build();
    }

    private void checkCodeUnique(String code, Long excludeId) {
        if (StringUtils.isBlank(code)) return;
        LambdaQueryWrapper<ReviewModelConfig> lqw = Wrappers.lambdaQuery();
        lqw.eq(ReviewModelConfig::getCode, code);
        lqw.ne(excludeId != null, ReviewModelConfig::getId, excludeId);
        if (baseMapper.exists(lqw)) {
            throw new RuntimeException("编码【" + code + "】已存在，请换一个");
        }
    }

    // ==================== 测试连接 ====================

    /**
     * OCR 类 provider 连接测试用的最小样本图。
     * <p>
     * 必须是「不透明、有实际内容、尺寸足够」的图：
     * PaddleX PP-OCRv5 serve 对 1x1 / 全透明的图会返回 HTTP 422「Invalid input file」，
     * qwen3-vl-plus 也要求长宽至少 10px。这里运行时生成一张 320x80 白底黑字「OCR TEST」PNG，
     * 既能通过输入校验，又能让 OCR 真正跑通管线（返回 errorCode=0，甚至识别出文字）。
     */
    private static final byte[] TEST_IMAGE_PNG = buildTestImagePng();

    private static byte[] buildTestImagePng() {
        try {
            int w = 320, h = 80;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.setColor(Color.BLACK);
            g.setFont(g.getFont().deriveFont(36f));
            g.drawString("OCR TEST", 24, 52);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            // 理论上不会失败；兜底返回 16x16 不透明白底 PNG（仍优于全透明）
            return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAIAAACQkWg2AAAAFklEQVR4nGP8//8/Aw"
                + "MDAwMDEwMDAwMDAB8YBAUVj7BLAAAAAElFTkSuQmCC");
        }
    }

    @Override
    public ReviewModelConfigTestVo testConnection(ReviewModelConfigBo bo) {
        if (bo == null) return ReviewModelConfigTestVo.fail("invalid", 0, "配置为空", "");
        AiModelConfigDto cfg = boToDto(bo);
        String provider = cfg.getProvider() == null ? "" : cfg.getProvider().toLowerCase(Locale.ROOT);
        String endpoint = (cfg.getBaseUrl() == null ? "" : cfg.getBaseUrl()) + " | " + cfg.getModelName();
        long start = System.currentTimeMillis();
        try {
            switch (provider) {
                case "ollama":
                case "dashscope":
                    return testChat(cfg, endpoint, start);
                case "paddleocr":
                    return testPaddleOcr(cfg, endpoint, start);
                case "qwen-vl-ocr":
                    return testQwenVlOcr(cfg, endpoint, start);
                default:
                    return ReviewModelConfigTestVo.fail("unsupported",
                        System.currentTimeMillis() - start,
                        "不支持的 provider: " + cfg.getProvider(), endpoint);
            }
        } catch (Exception e) {
            return ReviewModelConfigTestVo.fail("exception",
                System.currentTimeMillis() - start,
                e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                endpoint);
        }
    }

    private ReviewModelConfigTestVo testChat(AiModelConfigDto cfg, String endpoint, long start) {
        try {
            String reply = aiChatService.chatWithConfig(cfg, "你是测试助手", "ping");
            String sample = reply == null ? "" : reply.trim();
            if (sample.length() > 200) sample = sample.substring(0, 200) + "...";
            return ReviewModelConfigTestVo.success("chat ping",
                System.currentTimeMillis() - start,
                sample.isEmpty() ? "调用成功，返回内容为空" : sample, endpoint);
        } catch (Exception e) {
            return ReviewModelConfigTestVo.fail("chat ping",
                System.currentTimeMillis() - start,
                e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                endpoint);
        }
    }

    private ReviewModelConfigTestVo testPaddleOcr(AiModelConfigDto cfg, String endpoint, long start) {
        // 发一张 320x80 白底黑字 PNG 到 PaddleX serve，能拿到 errorCode=0 即认为通。
        // 不能用 1x1 / 全透明图：PaddleX 会返回 HTTP 422「Invalid input file」。
        if (cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank()) {
            return ReviewModelConfigTestVo.fail("paddleocr", 0, "PaddleOCR 必须填 baseUrl", endpoint);
        }
        try {
            RestTemplate rt = new RestTemplate();
            Map<String, Object> body = new HashMap<>();
            body.put("file", Base64.getEncoder().encodeToString(TEST_IMAGE_PNG));
            body.put("fileType", 1);
            body.put("useDocOrientationClassify", false);
            body.put("useDocUnwarping", false);
            body.put("useTextlineOrientation", false);
            // PaddleX serve 强制要求 application/json，否则 422
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            String resp = rt.postForObject(cfg.getBaseUrl(), entity, String.class);
            // resp 可能很长，只取 errorCode/errorMsg 段
            String msg = resp == null ? "(empty)" : (resp.length() > 200 ? resp.substring(0, 200) + "..." : resp);
            return ReviewModelConfigTestVo.success("paddleocr 测试图",
                System.currentTimeMillis() - start, msg, endpoint);
        } catch (RestClientResponseException re) {
            // 把 PaddleX serve 返回的响应体带出来，方便定位 422/500 等具体原因
            String respBody = re.getResponseBodyAsString();
            if (respBody != null && respBody.length() > 300) respBody = respBody.substring(0, 300) + "...";
            return ReviewModelConfigTestVo.fail("paddleocr 测试图",
                System.currentTimeMillis() - start,
                "HTTP " + re.getRawStatusCode() + " " + re.getStatusText()
                    + (respBody == null || respBody.isBlank() ? "" : " | " + respBody),
                endpoint);
        } catch (Exception e) {
            return ReviewModelConfigTestVo.fail("paddleocr 测试图",
                System.currentTimeMillis() - start,
                e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                endpoint);
        }
    }

    private ReviewModelConfigTestVo testQwenVlOcr(AiModelConfigDto cfg, String endpoint, long start) {
        try {
            // 复用 OcrProviderFactory.ocrWithConfig 但配置不在 DB 里，直接走 AiChatService
            ByteArrayResource res = new ByteArrayResource(TEST_IMAGE_PNG) {
                @Override public String getFilename() { return "ocr-test.png"; }
            };
            String text = aiChatService.ocrImage(res);
            return ReviewModelConfigTestVo.success("qwen-vl-ocr 测试图",
                System.currentTimeMillis() - start,
                "调用成功，识别结果：" + (text == null ? "" : text.trim()), endpoint);
        } catch (Exception e) {
            return ReviewModelConfigTestVo.fail("qwen-vl-ocr 测试图",
                System.currentTimeMillis() - start,
                e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                endpoint);
        }
    }

    private AiModelConfigDto boToDto(ReviewModelConfigBo bo) {
        Map<String, Object> opts = null;
        if (StringUtils.isNotBlank(bo.getExtraOptions())) {
            try { opts = JSON.parseObject(bo.getExtraOptions()); } catch (Exception ignored) {}
        }
        return AiModelConfigDto.builder()
            .id(bo.getId() == null ? -1L : bo.getId())  // 测试不落库，给个临时 id 防 NPE
            .code(bo.getCode())
            .provider(bo.getProvider())
            .modelName(bo.getModelName())
            .baseUrl(bo.getBaseUrl())
            .apiKey(bo.getApiKey())
            .numCtx(bo.getNumCtx())
            .numPredict(bo.getNumPredict())
            .maxTokens(bo.getMaxTokens())
            .temperature(bo.getTemperature() == null ? BigDecimal.valueOf(0.2) : bo.getTemperature())
            .topP(bo.getTopP() == null ? BigDecimal.valueOf(0.8) : bo.getTopP())
            .timeoutMs(bo.getTimeoutMs() == null ? 60000L : bo.getTimeoutMs())
            .kvCacheType(bo.getKvCacheType())
            .purpose(bo.getPurpose())
            .extraOptions(opts)
            .build();
    }
}

package org.dromara.common.ai.ocr;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.dto.AiModelConfigDto;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地 PaddleX serve OCR 实现（PP-OCRv5）。
 * <p>
 * 当前优先级：DB 配置（review_model_config purpose=ocr provider=paddleocr）
 * → yml 配置（review.ocr.paddleocr.*）兜底。
 * <p>
 * 不再使用 {@code @ConditionalOnProperty}，无条件注册到容器，由 {@link OcrProviderFactory}
 * 根据运行时配置动态选取 / 切换。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaddleOcrProvider implements OcrProvider {

    public static final String NAME = "paddleocr";

    private final OcrProperties properties;

    /** 按 (url, timeoutMs) 缓存 RestTemplate 实例，配置变更时重建 */
    private final Map<String, RestTemplate> restTemplateCache = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String ocr(Resource imageResource) {
        return ocr(imageResource, null);
    }

    @Override
    public String ocr(Resource imageResource, AiModelConfigDto override) {
        long start = System.currentTimeMillis();

        // 解析运行时参数：DB 配置优先，yml 兜底
        String url = (override != null && override.getBaseUrl() != null && !override.getBaseUrl().isBlank())
            ? override.getBaseUrl()
            : properties.getPaddleocr().getUrl();
        int timeoutMs = (override != null && override.getTimeoutMs() != null && override.getTimeoutMs() > 0)
            ? override.getTimeoutMs().intValue()
            : properties.getPaddleocr().getTimeoutMs();
        Map<String, Object> opts = override != null ? override.getExtraOptions() : null;
        double minConf = readDouble(opts, "confidenceThreshold",
            properties.getPaddleocr().getConfidenceThreshold());

        // 1) 图片读成 base64
        String base64;
        try (InputStream in = imageResource.getInputStream()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
            }
            base64 = Base64.getEncoder().encodeToString(buf.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("PaddleOCR: 读取图片失败", e);
        }

        // 2) 构造请求体（PaddleX serve 协议：{file, fileType:1=图片, 0=PDF}）
        Map<String, Object> body = new HashMap<>();
        body.put("file", base64);
        body.put("fileType", 1);
        body.put("useDocOrientationClassify", readBool(opts, "useDocOrientationClassify", false));
        body.put("useDocUnwarping", readBool(opts, "useDocUnwarping", false));
        body.put("useTextlineOrientation", readBool(opts, "useTextlineOrientation", false));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        // 3) 调用
        String json;
        try {
            json = restTemplateFor(url, timeoutMs).postForObject(url, entity, String.class);
        } catch (Exception e) {
            throw new RuntimeException("PaddleOCR 调用失败: url=" + url + ", err=" + e.getMessage(), e);
        }

        // 4) 解析响应
        StringBuilder sb = new StringBuilder();
        try {
            JSONObject root = JSON.parseObject(json);
            Integer code = root.getInteger("errorCode");
            if (code != null && code != 0) {
                throw new RuntimeException("PaddleOCR 业务错误: code=" + code
                    + ", msg=" + root.getString("errorMsg"));
            }
            JSONObject result = root.getJSONObject("result");
            if (result == null) return "";
            JSONArray ocrResults = result.getJSONArray("ocrResults");
            if (ocrResults == null || ocrResults.isEmpty()) return "";

            for (int i = 0; i < ocrResults.size(); i++) {
                JSONObject pageResult = ocrResults.getJSONObject(i);
                JSONObject pruned = pageResult.getJSONObject("prunedResult");
                if (pruned == null) continue;
                JSONArray texts = pruned.getJSONArray("rec_texts");
                JSONArray scores = pruned.getJSONArray("rec_scores");
                if (texts == null) continue;
                for (int j = 0; j < texts.size(); j++) {
                    Double score = (scores != null && j < scores.size()) ? scores.getDouble(j) : null;
                    if (score != null && score < minConf) continue;
                    String text = texts.getString(j);
                    if (text != null && !text.isBlank()) {
                        sb.append(text).append('\n');
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("PaddleOCR 响应解析失败: " + e.getMessage()
                + ", raw=" + (json == null ? "null" : json.substring(0, Math.min(200, json.length()))), e);
        }

        String text = sb.toString();
        log.info("[PaddleOcrProvider] OCR 完成: {} 字符, {} ms, url={}",
            text.length(), System.currentTimeMillis() - start, url);
        return text;
    }

    private RestTemplate restTemplateFor(String url, int timeoutMs) {
        String key = url + "@" + timeoutMs;
        return restTemplateCache.computeIfAbsent(key, k -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(10));
            factory.setReadTimeout(Duration.ofMillis(timeoutMs));
            return new RestTemplate(factory);
        });
    }

    private double readDouble(Map<String, Object> m, String key, double dft) {
        if (m == null) return dft;
        Object v = m.get(key);
        if (v == null) return dft;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return dft; }
    }

    private boolean readBool(Map<String, Object> m, String key, boolean dft) {
        if (m == null) return dft;
        Object v = m.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s);
        return dft;
    }
}

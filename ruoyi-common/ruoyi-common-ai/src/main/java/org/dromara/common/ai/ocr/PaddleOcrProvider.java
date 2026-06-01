package org.dromara.common.ai.ocr;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.ai.dto.AiModelConfigDto;
import org.dromara.common.ai.ocr.dto.OcrPageResult;
import org.dromara.common.ai.ocr.dto.OcrTextBlock;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
        OcrPageResult page = ocrStructured(imageResource, override);
        // 降级为纯文本:每块一行,过滤已在结构化阶段做完
        StringBuilder sb = new StringBuilder();
        for (OcrTextBlock b : page.getBlocks()) {
            if (b.getText() != null && !b.getText().isBlank()) {
                sb.append(b.getText()).append('\n');
            }
        }
        return sb.toString();
    }

    @Override
    public OcrPageResult ocrStructured(Resource imageResource, AiModelConfigDto override) {
        long start = System.currentTimeMillis();
        Params p = resolveParams(override);
        String base64 = readToBase64(imageResource);
        String json = callOcrApi(base64, p);
        OcrPageResult result = parseStructured(json, p.minConf);
        result.setElapsedMs(System.currentTimeMillis() - start);
        log.info("[PaddleOcrProvider] OCR 完成: {} blocks, {} ms, url={}",
            result.getBlocks().size(), result.getElapsedMs(), p.url);
        return result;
    }

    /** 运行时参数 */
    private static final class Params {
        String url;
        int timeoutMs;
        double minConf;
        Map<String, Object> opts;
    }

    private Params resolveParams(AiModelConfigDto override) {
        Params p = new Params();
        p.url = (override != null && override.getBaseUrl() != null && !override.getBaseUrl().isBlank())
            ? override.getBaseUrl() : properties.getPaddleocr().getUrl();
        p.timeoutMs = (override != null && override.getTimeoutMs() != null && override.getTimeoutMs() > 0)
            ? override.getTimeoutMs().intValue() : properties.getPaddleocr().getTimeoutMs();
        p.opts = override != null ? override.getExtraOptions() : null;
        p.minConf = readDouble(p.opts, "confidenceThreshold",
            properties.getPaddleocr().getConfidenceThreshold());
        return p;
    }

    private String readToBase64(Resource imageResource) {
        try (InputStream in = imageResource.getInputStream()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
            return Base64.getEncoder().encodeToString(buf.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("PaddleOCR: 读取图片失败", e);
        }
    }

    private String callOcrApi(String base64, Params p) {
        Map<String, Object> body = new HashMap<>();
        body.put("file", base64);
        body.put("fileType", 1);
        // useDocUnwarping 默认开:扫描件/手写件纸张形变会让相邻数字粘连(实测手写18位身份证
        // 因此掉成17位,且属中间漏位,事后补校验位救不回),去扭曲后识别完整,CPU 耗时仅 +5%。
        // 另两项保持关闭(默认):本管线上游已逐页正向渲染,方向分类/文本行方向收益小且增开销。
        // 三项均可经 DB review_model_config 的 extraOptions / yml 覆盖。
        body.put("useDocOrientationClassify", readBool(p.opts, "useDocOrientationClassify", false));
        body.put("useDocUnwarping", readBool(p.opts, "useDocUnwarping", true));
        body.put("useTextlineOrientation", readBool(p.opts, "useTextlineOrientation", false));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            return restTemplateFor(p.url, p.timeoutMs)
                .postForObject(p.url, new HttpEntity<>(body, headers), String.class);
        } catch (Exception e) {
            throw new RuntimeException("PaddleOCR 调用失败: url=" + p.url + ", err=" + e.getMessage(), e);
        }
    }

    /** 解析 PaddleX serve 响应为结构化结果。imageWidth/Height 从 inputImage 解析失败时为 0 */
    private OcrPageResult parseStructured(String json, double minConf) {
        try {
            JSONObject root = JSON.parseObject(json);
            Integer code = root.getInteger("errorCode");
            if (code != null && code != 0) {
                throw new RuntimeException("PaddleOCR 业务错误: code=" + code
                    + ", msg=" + root.getString("errorMsg"));
            }
            JSONObject result = root.getJSONObject("result");
            if (result == null) return emptyPage();
            JSONArray ocrResults = result.getJSONArray("ocrResults");
            if (ocrResults == null || ocrResults.isEmpty()) return emptyPage();

            // 单页输入只返回一个 page;多页(PDF)由调用方逐页分发,这里只取首页
            JSONObject pageResult = ocrResults.getJSONObject(0);
            JSONObject pruned = pageResult.getJSONObject("prunedResult");
            if (pruned == null) return emptyPage();

            List<OcrTextBlock> blocks = extractBlocks(pruned, minConf);
            int[] wh = readImageSize(pageResult.getString("inputImage"));
            return new OcrPageResult(wh[0], wh[1], blocks, 0L);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            String preview = json == null ? "null" : json.substring(0, Math.min(200, json.length()));
            throw new RuntimeException("PaddleOCR 响应解析失败: " + e.getMessage() + ", raw=" + preview, e);
        }
    }

    private List<OcrTextBlock> extractBlocks(JSONObject pruned, double minConf) {
        JSONArray texts = pruned.getJSONArray("rec_texts");
        JSONArray scores = pruned.getJSONArray("rec_scores");
        JSONArray polys = pruned.getJSONArray("rec_polys");
        if (texts == null) return Collections.emptyList();
        List<OcrTextBlock> blocks = new ArrayList<>(texts.size());
        for (int j = 0; j < texts.size(); j++) {
            Double score = (scores != null && j < scores.size()) ? scores.getDouble(j) : null;
            if (score != null && score < minConf) continue;
            String text = texts.getString(j);
            if (text == null || text.isBlank()) continue;
            List<int[]> poly = parsePoly(polys, j);
            blocks.add(new OcrTextBlock(text, score, poly));
        }
        return blocks;
    }

    private List<int[]> parsePoly(JSONArray polys, int idx) {
        if (polys == null || idx >= polys.size()) return Collections.emptyList();
        JSONArray pts = polys.getJSONArray(idx);
        if (pts == null) return Collections.emptyList();
        List<int[]> out = new ArrayList<>(pts.size());
        for (int k = 0; k < pts.size(); k++) {
            JSONArray xy = pts.getJSONArray(k);
            if (xy == null || xy.size() < 2) continue;
            out.add(new int[]{xy.getIntValue(0), xy.getIntValue(1)});
        }
        return out;
    }

    /** 从 base64 jpeg/png 解析图片宽高(SOF 段),失败时返回 (0,0) */
    private int[] readImageSize(String base64Img) {
        if (base64Img == null) return new int[]{0, 0};
        try {
            byte[] bytes = Base64.getDecoder().decode(base64Img);
            // PNG: bytes[16..19]=W, [20..23]=H (big-endian)
            if (bytes.length >= 24 && bytes[0] == (byte) 0x89 && bytes[1] == 'P'
                && bytes[2] == 'N' && bytes[3] == 'G') {
                int w = ((bytes[16] & 0xff) << 24) | ((bytes[17] & 0xff) << 16)
                    | ((bytes[18] & 0xff) << 8) | (bytes[19] & 0xff);
                int h = ((bytes[20] & 0xff) << 24) | ((bytes[21] & 0xff) << 16)
                    | ((bytes[22] & 0xff) << 8) | (bytes[23] & 0xff);
                return new int[]{w, h};
            }
            // JPEG: 找 SOF0/SOF2 段
            for (int i = 0; i < bytes.length - 1; i++) {
                if ((bytes[i] & 0xff) == 0xff
                    && ((bytes[i + 1] & 0xff) == 0xc0 || (bytes[i + 1] & 0xff) == 0xc2)) {
                    int h = ((bytes[i + 5] & 0xff) << 8) | (bytes[i + 6] & 0xff);
                    int w = ((bytes[i + 7] & 0xff) << 8) | (bytes[i + 8] & 0xff);
                    return new int[]{w, h};
                }
            }
        } catch (Exception ignored) {}
        return new int[]{0, 0};
    }

    private OcrPageResult emptyPage() {
        return new OcrPageResult(0, 0, Collections.emptyList(), 0L);
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

package org.dromara.common.ai.ocr;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

/**
 * 本地 PaddleX serve OCR 实现（PP-OCRv5）
 * <p>
 * 调用 PaddleX serve 的 {@code POST /ocr} 接口，入参为 base64 编码的图片，返回包含
 * {@code prunedResult.rec_texts} 与 {@code rec_scores} 的结构化结果。
 * <p>
 * 部署方式（4090 服务器，CPU 推理就够）：
 * <pre>
 * # 1. 起一个 PaddleX 容器
 * docker run -d --name paddlex-serve -p 8080:8080 \
 *   --restart unless-stopped \
 *   ccr-2vdh3abv-pub.cnc.bj.baidubce.com/paddlex/paddlex:paddlex3.0.0-paddlepaddle3.0.0-cpu \
 *   tail -f /dev/null
 *
 * # 2. 安装 OCR 依赖 + 启动 serve
 * docker exec -d paddlex-serve bash -c \
 *   "pip install -U paddleocr && \
 *    paddlex --serve --pipeline OCR --host 0.0.0.0 --port 8080"
 *
 * # 3. 验证
 * curl -X POST http://localhost:8080/ocr \
 *   -H 'Content-Type: application/json' \
 *   -d "{\"file\":\"$(base64 -w0 test.png)\",\"fileType\":1}"
 * </pre>
 * <p>
 * 仅在 {@code review.ocr.provider=paddleocr} 时注册，避免无服务时启动报错。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "review.ocr", name = "provider", havingValue = "paddleocr")
public class PaddleOcrProvider implements OcrProvider {

    public static final String NAME = "paddleocr";

    private final OcrProperties properties;
    private RestTemplate restTemplate;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String ocr(Resource imageResource) {
        long start = System.currentTimeMillis();

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
        // 关闭无关的预处理流水（仅做检测+识别），减少 CPU 开销
        body.put("useDocOrientationClassify", false);
        body.put("useDocUnwarping", false);
        body.put("useTextlineOrientation", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        // 3) 调用
        String json;
        try {
            json = restTemplate().postForObject(properties.getPaddleocr().getUrl(), entity, String.class);
        } catch (Exception e) {
            throw new RuntimeException("PaddleOCR 调用失败: url=" + properties.getPaddleocr().getUrl()
                + ", err=" + e.getMessage(), e);
        }

        // 4) 解析响应
        // {"logId":"...","errorCode":0,"errorMsg":"Success",
        //  "result":{"ocrResults":[{"prunedResult":{"rec_texts":["..."],"rec_scores":[0.99]}}]}}
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

            double minConf = properties.getPaddleocr().getConfidenceThreshold();
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
        log.info("[PaddleOcrProvider] OCR 完成: {} 字符, {} ms",
            text.length(), System.currentTimeMillis() - start);
        return text;
    }

    private RestTemplate restTemplate() {
        if (restTemplate == null) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(10));
            factory.setReadTimeout(Duration.ofMillis(properties.getPaddleocr().getTimeoutMs()));
            this.restTemplate = new RestTemplate(factory);
        }
        return restTemplate;
    }
}

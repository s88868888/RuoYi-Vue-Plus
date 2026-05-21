package org.dromara.common.ai.ocr;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OCR Provider 配置
 * <p>
 * 默认 provider=qwen-vl 维持原有行为；切到 paddleocr 时需先把 PaddleX serve 部署起来：
 * <pre>
 * docker run -d --name paddlex-serve -p 8080:8080 --restart unless-stopped \
 *   ccr-2vdh3abv-pub.cnc.bj.baidubce.com/paddlex/paddlex:paddlex3.0.0-paddlepaddle3.0.0-cpu \
 *   tail -f /dev/null
 * docker exec -d paddlex-serve bash -c \
 *   "pip install -U paddleocr && paddlex --serve --pipeline OCR --host 0.0.0.0 --port 8080"
 * </pre>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "review.ocr")
public class OcrProperties {

    /** 当前启用的 Provider，可选：qwen-vl / paddleocr */
    private String provider = "qwen-vl";

    /** PaddleOCR 配置（provider=paddleocr 时启用） */
    private Paddle paddleocr = new Paddle();

    @Data
    public static class Paddle {
        /** PaddleX serve OCR 接口（宿主机端口 8086 映射到容器 8080，POST /ocr） */
        private String url = "http://192.168.169.205:8086/ocr";

        /** 单张图片识别超时（毫秒），CPU 推理较慢预留 60 秒 */
        private int timeoutMs = 60000;

        /** 置信度阈值，低于此值的识别结果被丢弃 */
        private double confidenceThreshold = 0.5;
    }
}

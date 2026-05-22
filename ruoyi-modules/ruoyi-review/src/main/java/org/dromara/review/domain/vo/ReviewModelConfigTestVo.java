package org.dromara.review.domain.vo;

import lombok.Data;

/**
 * AI 模型配置「测试连接」结果
 */
@Data
public class ReviewModelConfigTestVo {

    /** 测试是否通过 */
    private Boolean ok;

    /** 耗时（毫秒） */
    private Long durationMs;

    /** 用例描述（如：chat hello / GET /api/version / OCR 1x1 sample） */
    private String testCase;

    /** 详细信息：成功时是返回内容摘要，失败时是错误堆栈摘要 */
    private String message;

    /** 实际调用的端点（baseUrl + 模型名） */
    private String endpoint;

    public static ReviewModelConfigTestVo success(String testCase, long durationMs, String message, String endpoint) {
        ReviewModelConfigTestVo vo = new ReviewModelConfigTestVo();
        vo.setOk(true);
        vo.setTestCase(testCase);
        vo.setDurationMs(durationMs);
        vo.setMessage(message);
        vo.setEndpoint(endpoint);
        return vo;
    }

    public static ReviewModelConfigTestVo fail(String testCase, long durationMs, String message, String endpoint) {
        ReviewModelConfigTestVo vo = new ReviewModelConfigTestVo();
        vo.setOk(false);
        vo.setTestCase(testCase);
        vo.setDurationMs(durationMs);
        vo.setMessage(message);
        vo.setEndpoint(endpoint);
        return vo;
    }
}

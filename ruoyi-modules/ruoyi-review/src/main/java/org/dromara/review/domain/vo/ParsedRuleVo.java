package org.dromara.review.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 抽取/Excel 导入的规则预览对象（未落库）
 *
 * @author ruoyi
 * @date 2026-05-12
 */
@Data
public class ParsedRuleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 规则内容
     */
    private String content;

    /**
     * 严重程度：must / should / suggest
     */
    private String severity;

    /**
     * 规则分类
     */
    private String category;

    /**
     * 检查字段（英文变量名）
     */
    private String checkField;

    /**
     * 检查方式：ai_extract / compare / exact_match / keyword
     */
    private String checkMethod;

    /**
     * 权重
     */
    private Integer weight;

    /**
     * 备注
     */
    private String remark;
}

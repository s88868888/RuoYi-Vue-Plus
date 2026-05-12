package org.dromara.review.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 规则导出视图（仅包含业务字段）
 */
@Data
@ExcelIgnoreUnannotated
public class ReviewStandardRuleExportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "规则内容")
    private String content;

    @ExcelProperty(value = "严重程度")
    private String severity;

    @ExcelProperty(value = "规则分类")
    private String category;

    @ExcelProperty(value = "权重")
    private Integer weight;
}

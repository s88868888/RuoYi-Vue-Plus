package org.dromara.review.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 审核规则 Excel 模板导入对象
 *
 * @author ruoyi
 * @date 2026-05-12
 */
@Data
@ExcelIgnoreUnannotated
public class ReviewRuleImportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "规则内容*", index = 0)
    private String content;

    @ExcelProperty(value = "严重程度*", index = 1)
    private String severity;

    @ExcelProperty(value = "规则分类", index = 2)
    private String category;

    @ExcelProperty(value = "备注", index = 3)
    private String remark;
}

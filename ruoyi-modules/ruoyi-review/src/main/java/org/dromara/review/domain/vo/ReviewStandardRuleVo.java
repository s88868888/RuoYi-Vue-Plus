package org.dromara.review.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.review.domain.ReviewStandardRule;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 审核标准规则视图对象 review_standard_rule
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ReviewStandardRule.class)
public class ReviewStandardRuleVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @ExcelProperty(value = "主键ID")
    private Long id;

    /**
     * 标准ID
     */
    @ExcelProperty(value = "标准ID")
    private Long standardId;

    /**
     * 规则内容
     */
    @ExcelProperty(value = "规则内容")
    private String content;

    /**
     * 规则分类
     */
    @ExcelProperty(value = "规则分类")
    private String category;

    /**
     * 严重程度
     */
    @ExcelProperty(value = "严重程度")
    private String severity;

    /**
     * 检查字段
     */
    @ExcelProperty(value = "检查字段")
    private String checkField;

    /**
     * 检查方法
     */
    @ExcelProperty(value = "检查方法")
    private String checkMethod;

    /**
     * 权重
     */
    @ExcelProperty(value = "权重")
    private Integer weight;

    /**
     * 置信度
     */
    @ExcelProperty(value = "置信度")
    private BigDecimal confidence;

    /**
     * 命中次数
     */
    @ExcelProperty(value = "命中次数")
    private Integer hitCount;

    /**
     * 未命中次数
     */
    @ExcelProperty(value = "未命中次数")
    private Integer missCount;

    /**
     * 排序顺序
     */
    @ExcelProperty(value = "排序顺序")
    private Integer sortOrder;

    /**
     * 状态
     */
    @ExcelProperty(value = "状态")
    private String status;

    /**
     * 是否启用关注（0=不关注 1=关注）
     */
    private String focusEnabled;

    /**
     * 关注要点（focusEnabled=1时生效，可多要点用换行或分号分隔，AI按要点提取文档原文片段供精确定位）
     */
    private String focusKeyword;

    /**
     * 创建时间
     */
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}

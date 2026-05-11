package org.dromara.review.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.review.domain.ReviewResultItem;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 审核结果明细视图对象 review_result_item
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ReviewResultItem.class)
public class ReviewResultItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @ExcelProperty(value = "主键ID")
    private Long id;

    /**
     * 任务ID
     */
    @ExcelProperty(value = "任务ID")
    private Long taskId;

    /**
     * 字段名称
     */
    @ExcelProperty(value = "字段名称")
    private String fieldName;

    /**
     * 字段标签
     */
    @ExcelProperty(value = "字段标签")
    private String fieldLabel;

    /**
     * 表单值
     */
    @ExcelProperty(value = "表单值")
    private String formValue;

    /**
     * 提取值
     */
    @ExcelProperty(value = "提取值")
    private String extractedValue;

    /**
     * 匹配状态
     */
    @ExcelProperty(value = "匹配状态")
    private String matchStatus;

    /**
     * 置信度
     */
    @ExcelProperty(value = "置信度")
    private BigDecimal confidence;

    /**
     * 严重程度
     */
    @ExcelProperty(value = "严重程度")
    private String severity;

    /**
     * 规则ID
     */
    @ExcelProperty(value = "规则ID")
    private Long ruleId;

    /**
     * 定位信息
     */
    @ExcelProperty(value = "定位信息")
    private String location;

    /**
     * 问题描述
     */
    @ExcelProperty(value = "问题描述")
    private String description;

    /**
     * 修改建议
     */
    @ExcelProperty(value = "修改建议")
    private String suggestion;

    /**
     * AI备注
     */
    private String aiRemark;

    /**
     * 是否误判
     */
    @ExcelProperty(value = "是否误判")
    private String misjudged;

    /**
     * 误判原因
     */
    @ExcelProperty(value = "误判原因")
    private String misjudgmentReason;

    /**
     * 段落ID
     */
    private Long paragraphId;

    /**
     * 排序顺序
     */
    private Integer sortOrder;

    /**
     * 原始JSON数据
     */
    private String rawData;

}

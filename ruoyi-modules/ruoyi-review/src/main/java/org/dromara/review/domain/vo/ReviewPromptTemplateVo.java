package org.dromara.review.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.review.domain.ReviewPromptTemplate;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * AI提示词模板视图对象 review_prompt_template
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ReviewPromptTemplate.class)
public class ReviewPromptTemplateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @ExcelProperty(value = "主键ID")
    private Long id;

    /**
     * 模板名称
     */
    @ExcelProperty(value = "模板名称")
    private String name;

    /**
     * 模板类型
     */
    @ExcelProperty(value = "模板类型")
    private String type;

    /**
     * 系统提示词
     */
    @ExcelProperty(value = "系统提示词")
    private String systemPrompt;

    /**
     * 用户提示词
     */
    @ExcelProperty(value = "用户提示词")
    private String userPrompt;

    /**
     * 输出格式
     */
    @ExcelProperty(value = "输出格式")
    private String outputFormat;

    /**
     * 模型名称
     */
    @ExcelProperty(value = "模型名称")
    private String modelName;

    /**
     * 温度参数
     */
    @ExcelProperty(value = "温度参数")
    private BigDecimal temperature;

    /**
     * 状态
     */
    @ExcelProperty(value = "状态")
    private String status;

    /**
     * 备注
     */
    @ExcelProperty(value = "备注")
    private String remark;

    /**
     * 创建时间
     */
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}

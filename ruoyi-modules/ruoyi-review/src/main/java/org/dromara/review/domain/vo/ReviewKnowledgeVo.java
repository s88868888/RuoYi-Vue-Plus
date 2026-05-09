package org.dromara.review.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.review.domain.ReviewKnowledge;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 审核知识库视图对象 review_knowledge
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ReviewKnowledge.class)
public class ReviewKnowledgeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @ExcelProperty(value = "主键ID")
    private Long id;

    /**
     * 知识库名称
     */
    @ExcelProperty(value = "知识库名称")
    private String name;

    /**
     * 知识库类型
     */
    @ExcelProperty(value = "知识库类型")
    private String type;

    /**
     * 知识库描述
     */
    @ExcelProperty(value = "知识库描述")
    private String description;

    /**
     * 文档数量
     */
    @ExcelProperty(value = "文档数量")
    private Integer docCount;

    /**
     * 案例数量
     */
    @ExcelProperty(value = "案例数量")
    private Integer caseCount;

    /**
     * 模式数量
     */
    @ExcelProperty(value = "模式数量")
    private Integer patternCount;

    /**
     * 准确率
     */
    @ExcelProperty(value = "准确率")
    private BigDecimal accuracy;

    /**
     * 状态
     */
    @ExcelProperty(value = "状态")
    private String status;

    /**
     * 向量集合
     */
    @ExcelProperty(value = "向量集合")
    private String vectorCollection;

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

    /**
     * 更新时间
     */
    @ExcelProperty(value = "更新时间")
    private Date updateTime;

}

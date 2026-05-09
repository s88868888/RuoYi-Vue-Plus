package org.dromara.review.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.review.domain.ReviewTask;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;


/**
 * 审核任务视图对象 review_task
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ReviewTask.class)
public class ReviewTaskVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @ExcelProperty(value = "主键ID")
    private Long id;

    /**
     * 任务名称
     */
    @ExcelProperty(value = "任务名称")
    private String taskName;

    /**
     * 任务类型
     */
    @ExcelProperty(value = "任务类型")
    private String taskType;

    /**
     * 来源ID
     */
    @ExcelProperty(value = "来源ID")
    private Long sourceId;

    /**
     * 来源类型
     */
    @ExcelProperty(value = "来源类型")
    private String sourceType;

    /**
     * 任务状态
     */
    @ExcelProperty(value = "任务状态")
    private String status;

    /**
     * 通过状态
     */
    @ExcelProperty(value = "通过状态")
    private String passStatus;

    /**
     * 评分
     */
    @ExcelProperty(value = "评分")
    private Integer score;

    /**
     * 版本号
     */
    @ExcelProperty(value = "版本号")
    private Integer version;

    /**
     * 父任务ID
     */
    @ExcelProperty(value = "父任务ID")
    private Long parentTaskId;

    /**
     * AI模型
     */
    @ExcelProperty(value = "AI模型")
    private String aiModel;

    /**
     * 审核耗时（毫秒）
     */
    @ExcelProperty(value = "审核耗时")
    private Long reviewDuration;

    /**
     * 表单快照
     */
    private String formSnapshot;

    /**
     * AI摘要
     */
    private String aiSummary;

    /**
     * 总规则数
     */
    @ExcelProperty(value = "总规则数")
    private Integer totalRules;

    /**
     * 通过数量
     */
    @ExcelProperty(value = "通过数量")
    private Integer passCount;

    /**
     * 错误数量
     */
    @ExcelProperty(value = "错误数量")
    private Integer errorCount;

    /**
     * 警告数量
     */
    @ExcelProperty(value = "警告数量")
    private Integer warningCount;

    /**
     * 提示数量
     */
    @ExcelProperty(value = "提示数量")
    private Integer infoCount;

    /**
     * 误判数量
     */
    @ExcelProperty(value = "误判数量")
    private Integer misjudgedCount;

    /**
     * 审核人ID
     */
    @ExcelProperty(value = "审核人ID")
    private Long reviewerId;

    /**
     * 审核意见
     */
    @ExcelProperty(value = "审核意见")
    private String reviewComment;

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

    /**
     * 关联的标准名称（逗号拼接，非Entity字段，Service层手动设置）
     */
    private String standardNames;

}

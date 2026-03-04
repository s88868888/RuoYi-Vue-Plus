package org.dromara.resource.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;
import org.dromara.resource.domain.BizBidSubmission;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 投标项目视图对象 biz_bid_submission
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BizBidSubmission.class)
public class BizBidSubmissionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @ExcelProperty(value = "主键ID")
    private Long id;

    /**
     * 招标项目ID
     */
    @ExcelProperty(value = "招标项目ID")
    private Long bidProjectId;

    /**
     * 项目名称
     */
    @ExcelProperty(value = "项目名称")
    private String projectName;

    /**
     * 招标单位
     */
    @ExcelProperty(value = "招标单位")
    private String bidOrg;

    /**
     * 项目类型
     */
    @ExcelProperty(value = "项目类型")
    private String projectType;

    /**
     * 预算金额（元）
     */
    @ExcelProperty(value = "预算金额")
    private BigDecimal budgetAmount;

    /**
     * 项目地区
     */
    @ExcelProperty(value = "项目地区")
    private String projectRegion;

    /**
     * 招标方式
     */
    @ExcelProperty(value = "招标方式")
    private String bidMethod;

    /**
     * 项目描述
     */
    @ExcelProperty(value = "项目描述")
    private String projectDesc;

    /**
     * 投标状态
     */
    @ExcelProperty(value = "投标状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "bid_submission_status")
    private String submissionStatus;

    /**
     * 生成进度（0-100）
     */
    @ExcelProperty(value = "生成进度")
    private Integer generationProgress;

    /**
     * 选中的公司ID列表（JSON数组）
     */
    private String selectedCompanies;

    /**
     * 生成配置（JSON）
     */
    private String generationConfig;

    /**
     * 章节结构是否已生成
     */
    private String chapterStructureGenerated;

    /**
     * 总文档数
     */
    @ExcelProperty(value = "总文档数")
    private Integer totalDocuments;

    /**
     * 已完成文档数
     */
    @ExcelProperty(value = "已完成文档数")
    private Integer completedDocuments;

    /**
     * 失败文档数
     */
    @ExcelProperty(value = "失败文档数")
    private Integer failedDocuments;

    /**
     * 异步任务ID
     */
    private String taskId;

    /**
     * 开始生成时间
     */
    @ExcelProperty(value = "开始生成时间")
    private Date startTime;

    /**
     * 完成时间
     */
    @ExcelProperty(value = "完成时间")
    private Date endTime;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 工作流阶段：pending_config-待配置，configured-已配置，structure_generated-结构已生成，
     * generating-生成中，completed-已完成，failed-失败
     */
    private String workflowStage;

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

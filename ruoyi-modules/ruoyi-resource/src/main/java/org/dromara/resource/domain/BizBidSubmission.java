package org.dromara.resource.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 投标项目对象 biz_bid_submission
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_bid_submission")
public class BizBidSubmission extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 部门ID
     */
    private Long deptId;

    /**
     * 招标项目ID
     */
    private Long bidProjectId;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 招标单位
     */
    private String bidOrg;

    /**
     * 项目类型
     */
    private String projectType;

    /**
     * 预算金额（万元）
     */
    private BigDecimal budgetAmount;

    /**
     * 项目地区
     */
    private String projectRegion;

    /**
     * 招标方式
     */
    private String bidMethod;

    /**
     * 项目描述
     */
    private String projectDesc;

    /**
     * 投标状态：draft-草稿，generating-生成中，completed-已完成，failed-失败
     */
    private String submissionStatus;

    /**
     * 生成进度（0-100）
     */
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
    private Integer totalDocuments;

    /**
     * 已完成文档数
     */
    private Integer completedDocuments;

    /**
     * 失败文档数
     */
    private Integer failedDocuments;

    /**
     * 异步任务ID
     */
    private String taskId;

    /**
     * 开始生成时间
     */
    private Date startTime;

    /**
     * 完成时间
     */
    private Date endTime;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 备注
     */
    private String remark;

}

package org.dromara.resource.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.resource.domain.BizBidSubmission;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 投标项目业务对象 biz_bid_submission
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BizBidSubmission.class, reverseConvertGenerate = false)
public class BizBidSubmissionBo extends BaseEntity {

    /**
     * 主键ID
     */
    @NotNull(message = "主键ID不能为空", groups = { EditGroup.class })
    private Long id;

    /**
     * 招标项目ID
     */
    @NotNull(message = "招标项目ID不能为空", groups = { AddGroup.class })
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
     * 预算金额（元）
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
     * 投标状态
     */
    private String status;

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

    /**
     * 是否分析竞争对手（创建时传参）
     */
    private Boolean analyzeCompetitors;

    /**
     * 竞争对手分析自定义提示词（创建时传参，不持久化）
     */
    private String competitorAnalysisPrompt;

}

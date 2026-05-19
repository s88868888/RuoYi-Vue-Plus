package org.dromara.review.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 审核任务对象 review_task
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("review_task")
public class ReviewTask extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 任务类型
     */
    private String taskType;

    /**
     * 来源ID（兼容外部业务系统的UUID/字符串ID，如旧城更协议ID）
     */
    private String sourceId;

    /**
     * 来源类型
     */
    private String sourceType;

    /**
     * 任务状态
     */
    private String status;

    /**
     * 通过状态
     */
    private String passStatus;

    /**
     * 评分
     */
    private Integer score;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 父任务ID
     */
    private Long parentTaskId;

    /**
     * AI模型
     */
    private String aiModel;

    /**
     * 审核耗时（毫秒）
     */
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
     * AI完整响应JSON
     */
    private String resultJson;

    /**
     * AI审核报告Markdown
     */
    private String resultMarkdown;

    /**
     * 总规则数
     */
    private Integer totalRules;

    /**
     * 通过数量
     */
    private Integer passCount;

    /**
     * 错误数量
     */
    private Integer errorCount;

    /**
     * 警告数量
     */
    private Integer warningCount;

    /**
     * 提示数量
     */
    private Integer infoCount;

    /**
     * 误判数量
     */
    private Integer misjudgedCount;

    /**
     * 审核人ID
     */
    private Long reviewerId;

    /**
     * 审核意见
     */
    private String reviewComment;

    /**
     * 备注
     */
    private String remark;

}

package org.dromara.resource.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.util.Date;

/**
 * 标书章节结构对象 biz_submission_chapter
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_submission_chapter")
public class BizSubmissionChapter extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 投标项目ID
     */
    private Long bidSubmissionId;

    /**
     * 标书文档ID
     */
    private Long submissionDocumentId;

    /**
     * 父章节ID（0为根节点）
     */
    private Long parentId;

    /**
     * 章节编号（如：1.1.1）
     */
    private String chapterNo;

    /**
     * 章节标题
     */
    private String chapterTitle;

    /**
     * 章节层级（1/2/3/4）
     */
    private Integer chapterLevel;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 章节类型：template-模板章节，generate-AI生成，mixed-混合
     */
    private String chapterType;

    /**
     * 模板来源（从招标文件提取的模板内容）
     */
    private String templateSource;

    /**
     * 占位符列表（JSON数组）
     */
    private String templatePlaceholders;

    /**
     * 生成状态：pending-待生成，generating-生成中，completed-已完成，failed-失败
     */
    private String generationStatus;

    /**
     * 生成进度（0-100）
     */
    private Integer generationProgress;

    /**
     * 章节内容（Markdown格式）
     */
    private String chapterContent;

    /**
     * 原因说明（大章节需要）
     */
    private String reasonDescription;

    /**
     * 使用的提示词
     */
    private String aiPrompt;

    /**
     * 使用的AI模型
     */
    private String aiModel;

    /**
     * 消耗的Token数
     */
    private Integer aiTokensUsed;

    /**
     * 开始生成时间
     */
    private Date generationStartTime;

    /**
     * 完成时间
     */
    private Date generationEndTime;

    /**
     * 生成耗时（秒）
     */
    private Integer generationDuration;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 备注
     */
    private String remark;

}

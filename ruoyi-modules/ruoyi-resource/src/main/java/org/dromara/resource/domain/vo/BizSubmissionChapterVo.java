package org.dromara.resource.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.resource.domain.BizSubmissionChapter;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 标书章节结构视图对象 biz_submission_chapter
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Data
@AutoMapper(target = BizSubmissionChapter.class)
public class BizSubmissionChapterVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
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
     * 父章节ID
     */
    private Long parentId;

    /**
     * 章节编号
     */
    private String chapterNo;

    /**
     * 章节标题
     */
    private String chapterTitle;

    /**
     * 章节层级
     */
    private Integer chapterLevel;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 章节类型
     */
    private String chapterType;

    /**
     * 模板来源
     */
    private String templateSource;

    /**
     * 占位符列表
     */
    private String templatePlaceholders;

    /**
     * 生成状态
     */
    private String generationStatus;

    /**
     * 生成进度
     */
    private Integer generationProgress;

    /**
     * 章节内容
     */
    private String chapterContent;

    /**
     * 原因说明
     */
    private String reasonDescription;

    /**
     * AI提示词
     */
    private String aiPrompt;

    /**
     * AI模型
     */
    private String aiModel;

    /**
     * Token消耗
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
     * 生成耗时
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

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 子章节（用于树形结构）
     */
    private List<BizSubmissionChapterVo> children;

}

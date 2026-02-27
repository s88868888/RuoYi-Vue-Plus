package org.dromara.resource.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.util.Date;

/**
 * 文档生成日志对象 biz_submission_document_log
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Data
@TableName("biz_submission_document_log")
public class BizSubmissionDocumentLog extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 投标项目ID
     */
    private Long bidSubmissionId;

    /**
     * 标书文档ID
     */
    private Long submissionDocumentId;

    /**
     * 日志级别：INFO, WARN, ERROR
     */
    private String logLevel;

    /**
     * 日志消息
     */
    private String logMessage;

    /**
     * 详细信息（JSON）
     */
    private String logDetail;

    /**
     * 日志时间
     */
    private Date logTime;

    /**
     * 当前进度
     */
    private Integer progress;

    /**
     * 当前阶段
     */
    private String stage;

}

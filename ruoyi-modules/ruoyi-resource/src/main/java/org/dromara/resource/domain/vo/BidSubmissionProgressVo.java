package org.dromara.resource.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 投标项目进度视图对象
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Data
public class BidSubmissionProgressVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 投标项目ID
     */
    private Long submissionId;

    /**
     * 投标状态
     */
    private String status;

    /**
     * 整体进度（0-100）
     */
    private Integer overallProgress;

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
     * 文档进度列表
     */
    private List<DocumentProgressItem> documents;

    /**
     * 日志列表
     */
    private List<LogItem> logs;

    /**
     * 文档进度项
     */
    @Data
    public static class DocumentProgressItem implements Serializable {
        private Long documentId;
        private String companyName;
        private String documentType;
        private String documentTypeName;
        private Integer documentNo;
        private String generationStatus;
        private String statusText;
        private Integer progress;
        private String errorMessage;
    }

    /**
     * 日志项
     */
    @Data
    public static class LogItem implements Serializable {
        private String time;
        private String level;
        private String message;
        private String stage;
        private Integer progress;
    }

}

package org.dromara.review.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 审核任务附件对象 review_task_file
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("review_task_file")
public class ReviewTaskFile extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * OSS文件ID
     */
    private Long ossId;

    /**
     * 文件名称
     */
    private String fileName;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 解析状态
     */
    private String parseStatus;

    /**
     * 提取文本
     */
    private String extractedText;

    /**
     * OCR 后的可搜索PDF URL（扫描件用；打印件为空，查看器回退原文件）
     */
    private String searchableUrl;

    /**
     * OCR 状态 NONE=无需/非PDF SKIP=本就有文字层 PENDING/RUNNING/SUCCESS/FAIL
     */
    private String ocrStatus;

    /**
     * 排序顺序
     */
    private Integer sortOrder;

}

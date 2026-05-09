package org.dromara.review.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 审核文档段落对象 review_doc_paragraph
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@TableName("review_doc_paragraph")
public class ReviewDocParagraph implements Serializable {

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
     * 文件ID
     */
    private Long fileId;

    /**
     * 章节
     */
    private String chapter;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 排序顺序
     */
    private Integer sortOrder;

    /**
     * 租户编号
     */
    private String tenantId;

    /**
     * 创建时间
     */
    private Date createTime;

}

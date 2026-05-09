package org.dromara.review.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 审核任务与标准关联对象 review_task_standard
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@TableName("review_task_standard")
public class ReviewTaskStandard implements Serializable {

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
     * 标准ID
     */
    private Long standardId;

    /**
     * 创建时间
     */
    private Date createTime;

}

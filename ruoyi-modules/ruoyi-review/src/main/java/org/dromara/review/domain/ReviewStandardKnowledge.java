package org.dromara.review.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 审核标准与知识库关联对象 review_standard_knowledge
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@TableName("review_standard_knowledge")
public class ReviewStandardKnowledge implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 标准ID
     */
    private Long standardId;

    /**
     * 知识库ID
     */
    private Long knowledgeId;

    /**
     * 同步状态
     */
    private String syncStatus;

    /**
     * 创建时间
     */
    private Date createTime;

}

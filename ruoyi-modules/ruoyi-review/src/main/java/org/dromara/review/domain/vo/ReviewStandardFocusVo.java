package org.dromara.review.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.review.domain.ReviewStandardFocus;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 审核标准关注要点视图对象 review_standard_focus
 *
 * @author ruoyi
 * @date 2026-06-17
 */
@Data
@AutoMapper(target = ReviewStandardFocus.class)
public class ReviewStandardFocusVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 所属标准ID
     */
    private Long standardId;

    /**
     * 关注要点
     */
    private String keyword;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 状态（0=启用 1=停用）
     */
    private String status;

    /**
     * 创建时间
     */
    private Date createTime;

}

package org.dromara.review.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 审核标准关注要点对象 review_standard_focus
 *
 * @author ruoyi
 * @date 2026-06-17
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("review_standard_focus")
public class ReviewStandardFocus extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 所属标准ID
     */
    private Long standardId;

    /**
     * 关注要点（一个文本要点，AI据此在文档中提取对应原文片段供精确定位）
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

}

package org.dromara.review.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 审核标准对象 review_standard
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("review_standard")
public class ReviewStandard extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 标准名称
     */
    private String name;

    /**
     * 标准类型
     */
    private String type;

    /**
     * 版本号
     */
    private String version;

    /**
     * 描述
     */
    private String description;

    /**
     * 规则数量
     */
    private Integer ruleCount;

    /**
     * 使用次数
     */
    private Integer useCount;

    /**
     * 是否系统内置
     */
    private String isSystem;

    /**
     * 状态
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

}

package org.dromara.resource.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * AI提示词模板对象 biz_ai_prompt_template
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_ai_prompt_template")
public class BizAiPromptTemplate extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 关联部门ID
     */
    private Long deptId;

    /**
     * 模板名称
     */
    private String templateName;

    /**
     * 模板类型（bid_analysis招标分析 scoring_criteria评分标准 risk_assessment风险评估 other其他）
     */
    private String templateType;

    /**
     * 提示词内容
     */
    private String promptContent;

    /**
     * 是否系统模板（0否 1是）系统模板不可删除
     */
    private String isSystem;

    /**
     * 排序号
     */
    private Integer sortOrder;

    /**
     * 状态（0正常 1停用）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

}

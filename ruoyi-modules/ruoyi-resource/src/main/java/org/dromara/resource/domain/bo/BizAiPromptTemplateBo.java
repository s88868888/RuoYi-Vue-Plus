package org.dromara.resource.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.resource.domain.BizAiPromptTemplate;

import java.io.Serial;

/**
 * AI提示词模板业务对象
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BizAiPromptTemplate.class, reverseConvertGenerate = false)
public class BizAiPromptTemplateBo extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 关联部门ID
     */
    private Long deptId;

    /**
     * 模板名称
     */
    @NotBlank(message = "模板名称不能为空")
    private String templateName;

    /**
     * 模板类型（bid_analysis招标分析 scoring_criteria评分标准 risk_assessment风险评估 other其他）
     */
    @NotBlank(message = "模板类型不能为空")
    private String templateType;

    /**
     * 提示词内容
     */
    @NotBlank(message = "提示词内容不能为空")
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

}

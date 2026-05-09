package org.dromara.review.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.review.domain.ReviewKnowledgeCase;

/**
 * 审核知识库历史案例业务对象 review_knowledge_case
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ReviewKnowledgeCase.class, reverseConvertGenerate = false)
public class ReviewKnowledgeCaseBo extends BaseEntity {

    /**
     * 主键ID
     */
    @NotNull(message = "主键不能为空", groups = {EditGroup.class})
    private Long id;

    /**
     * 知识库ID
     */
    @NotNull(message = "知识库ID不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long knowledgeId;

    /**
     * 案例标题
     */
    @NotBlank(message = "案例标题不能为空", groups = {AddGroup.class, EditGroup.class})
    private String title;

    /**
     * 案例类型
     */
    private String caseType;

    /**
     * 应用场景
     */
    private String scenario;

    /**
     * 表单数据
     */
    private String formData;

    /**
     * 审核结论
     */
    private String reviewConclusion;

    /**
     * 关键要点
     */
    private String keyPoint;

}

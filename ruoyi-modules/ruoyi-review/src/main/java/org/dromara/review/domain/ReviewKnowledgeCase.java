package org.dromara.review.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 审核知识库历史案例对象 review_knowledge_case
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("review_knowledge_case")
public class ReviewKnowledgeCase extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 知识库ID
     */
    private Long knowledgeId;

    /**
     * 案例标题
     */
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

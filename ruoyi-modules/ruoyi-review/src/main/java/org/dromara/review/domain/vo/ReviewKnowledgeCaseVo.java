package org.dromara.review.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.review.domain.ReviewKnowledgeCase;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 审核知识库历史案例视图对象 review_knowledge_case
 *
 * @author ruoyi
 * @date 2026-05-09
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ReviewKnowledgeCase.class)
public class ReviewKnowledgeCaseVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @ExcelProperty(value = "主键ID")
    private Long id;

    /**
     * 知识库ID
     */
    @ExcelProperty(value = "知识库ID")
    private Long knowledgeId;

    /**
     * 案例标题
     */
    @ExcelProperty(value = "案例标题")
    private String title;

    /**
     * 案例类型
     */
    @ExcelProperty(value = "案例类型")
    private String caseType;

    /**
     * 应用场景
     */
    @ExcelProperty(value = "应用场景")
    private String scenario;

    /**
     * 表单数据
     */
    private String formData;

    /**
     * 审核结论
     */
    @ExcelProperty(value = "审核结论")
    private String reviewConclusion;

    /**
     * 关键要点
     */
    @ExcelProperty(value = "关键要点")
    private String keyPoint;

    /**
     * 创建时间
     */
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}

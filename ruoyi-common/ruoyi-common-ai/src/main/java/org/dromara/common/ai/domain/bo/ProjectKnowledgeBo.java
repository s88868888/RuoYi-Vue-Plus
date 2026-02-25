package org.dromara.common.ai.domain.bo;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 项目知识业务对象
 *
 * @author ruoyi
 */
@Data
public class ProjectKnowledgeBo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 标题
     */
    @NotBlank(message = "标题不能为空")
    private String title;

    /**
     * 内容
     */
    @NotBlank(message = "内容不能为空")
    private String content;

    /**
     * 分类
     */
    private String category;

    /**
     * 标签
     */
    private String tags;

    /**
     * 关键词
     */
    private String keywords;

    /**
     * 作者
     */
    private String author;

    /**
     * 创建日期
     */
    private String createDate;

}

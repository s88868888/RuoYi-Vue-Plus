package org.dromara.common.ai.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 向量搜索结果
 *
 * @author ruoyi
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorSearchResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文档ID
     */
    private String id;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 公司ID
     */
    private Long companyId;

    /**
     * 文档类型
     */
    private String docType;

    /**
     * 文档内容
     */
    private String content;

    /**
     * 相似度分数
     */
    private Float score;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;

}

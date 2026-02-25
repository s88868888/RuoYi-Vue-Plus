package org.dromara.common.ai.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 向量文档实体
 *
 * @author ruoyi
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorDocument implements Serializable {

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
     * 文档类型（company_info, personnel_info, product_info, qualification, performance, patent, financial, project）
     */
    private String docType;

    /**
     * 文档内容
     */
    private String content;

    /**
     * 向量数据
     */
    private float[] vector;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;

    /**
     * 创建时间
     */
    private Long createTime;

}

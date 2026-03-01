package org.dromara.resource.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

/**
 * 标书配置对象 biz_document_config
 *
 * @author ruoyi
 * @date 2026-03-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_document_config")
public class BizDocumentConfig extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 投标项目ID
     */
    private Long bidSubmissionId;

    /**
     * 公司ID
     */
    private Long companyId;

    /**
     * 公司名称
     */
    private String companyName;

    /**
     * 文档类型：commercial-商务标，technical-技术标，complete-整本标书
     */
    private String documentType;

    /**
     * 同类型序号（第几份）
     */
    private Integer documentNo;

    /**
     * 状态：active-有效，deleted-已删除
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

}

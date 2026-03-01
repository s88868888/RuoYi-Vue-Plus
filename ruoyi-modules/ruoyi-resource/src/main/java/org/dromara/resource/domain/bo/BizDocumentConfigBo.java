package org.dromara.resource.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.resource.domain.BizDocumentConfig;

/**
 * 标书配置业务对象 biz_document_config
 *
 * @author ruoyi
 * @date 2026-03-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BizDocumentConfig.class, reverseConvertGenerate = false)
public class BizDocumentConfigBo extends BaseEntity {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 投标项目ID
     */
    @NotNull(message = "投标项目ID不能为空")
    private Long bidSubmissionId;

    /**
     * 公司ID
     */
    @NotNull(message = "公司ID不能为空")
    private Long companyId;

    /**
     * 公司名称
     */
    private String companyName;

    /**
     * 文档类型：commercial-商务标，technical-技术标，complete-整本标书
     */
    @NotBlank(message = "文档类型不能为空")
    private String documentType;

    /**
     * 同类型序号（第几份）
     */
    @NotNull(message = "文档序号不能为空")
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

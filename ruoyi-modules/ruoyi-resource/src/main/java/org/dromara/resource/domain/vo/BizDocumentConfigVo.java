package org.dromara.resource.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.resource.domain.BizDocumentConfig;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 标书配置视图对象 biz_document_config
 *
 * @author ruoyi
 * @date 2026-03-01
 */
@Data
@AutoMapper(target = BizDocumentConfig.class)
public class BizDocumentConfigVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
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

    /**
     * 创建时间
     */
    private Date createTime;

}

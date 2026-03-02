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
     * 文档名称
     */
    private String documentName;

    /**
     * 生成状态：pending-待生成，generating-生成中，completed-已完成，failed-失败
     */
    private String generationStatus;

    /**
     * 生成进度 0-100
     */
    private Integer generationProgress;

    /**
     * 开始生成时间
     */
    private java.util.Date generationStartTime;

    /**
     * 生成结束时间
     */
    private java.util.Date generationEndTime;

    /**
     * 生成耗时（秒）
     */
    private Integer generationDuration;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 生成的文件路径
     */
    private String filePath;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 总章节数
     */
    private Integer totalChapters;

    /**
     * 已完成章节数
     */
    private Integer completedChapters;

    /**
     * 备注
     */
    private String remark;

}

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
    private Date generationStartTime;

    /**
     * 生成结束时间
     */
    private Date generationEndTime;

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

    /**
     * 创建时间
     */
    private Date createTime;

}

package org.dromara.resource.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.util.Date;

/**
 * 标书文档对象 biz_submission_document
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_submission_document")
public class BizSubmissionDocument extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 部门ID
     */
    private Long deptId;

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
     * 文档名称
     */
    private String documentName;

    /**
     * 文档类型：commercial-商务标，technical-技术标，complete-整本标书
     */
    private String documentType;

    /**
     * 同类型文档序号
     */
    private Integer documentNo;

    /**
     * 文档内容（Markdown格式）
     */
    private String documentContent;

    /**
     * 文档HTML内容
     */
    private String documentHtml;

    /**
     * 文档文件路径（PDF/Word）
     */
    private String filePath;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 生成状态：pending-待生成，generating-生成中，completed-已完成，failed-失败
     */
    private String generationStatus;

    /**
     * 生成进度（0-100）
     */
    private Integer generationProgress;

    /**
     * 开始生成时间
     */
    private Date generationStartTime;

    /**
     * 完成时间
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
     * 使用的AI模型
     */
    private String aiModel;

    /**
     * 使用的提示词
     */
    private String aiPrompt;

    /**
     * 消耗的Token数
     */
    private Integer aiTokensUsed;

    /**
     * 文档版本号
     */
    private Integer version;

    /**
     * 是否最新版本
     */
    private String isLatest;

    /**
     * 备注
     */
    private String remark;

}

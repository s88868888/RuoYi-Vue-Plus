package org.dromara.resource.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.resource.domain.BizBidProjectAttachment;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 招标文件附件视图对象 biz_bid_project_attachment
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Data
@AutoMapper(target = BizBidProjectAttachment.class)
public class BizBidProjectAttachmentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 招标项目ID
     */
    private Long bidProjectId;

    /**
     * 附件名称
     */
    private String attachmentName;

    /**
     * 附件类型
     */
    private String attachmentType;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 文件格式
     */
    private String fileFormat;

    /**
     * 解析状态
     */
    private String parseStatus;

    /**
     * 解析后的文本内容
     */
    private String parsedContent;

    /**
     * 解析后的结构化数据
     */
    private String parsedStructure;

    /**
     * 提取的模板内容
     */
    private String extractedTemplates;

    /**
     * 解析错误信息
     */
    private String parseError;

    /**
     * 解析时间
     */
    private Date parseTime;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private Date createTime;

}

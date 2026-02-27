package org.dromara.resource.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.util.Date;

/**
 * 招标文件附件对象 biz_bid_project_attachment
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_bid_project_attachment")
public class BizBidProjectAttachment extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id")
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
     * 附件类型：bid_doc-招标文件，supplement-补充说明
     */
    private String attachmentType;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件格式：pdf, docx
     */
    private String fileFormat;

    /**
     * 解析状态：pending-待解析，parsing-解析中，completed-已完成，failed-失败
     */
    private String parseStatus;

    /**
     * 解析后的文本内容
     */
    private String parsedContent;

    /**
     * 解析后的结构化数据（JSON）
     */
    private String parsedStructure;

    /**
     * 提取的模板内容（JSON数组）
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

}

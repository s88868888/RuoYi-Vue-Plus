package org.dromara.resource.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 招标文件附件视图对象（从 OSS 文件信息转换而来）
 *
 * @author ruoyi
 * @date 2026-02-26
 */
@Data
public class BizBidProjectAttachmentVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 招标项目ID
     */
    private Long bidProjectId;

    /**
     * 附件名称
     */
    private String attachmentName;

    /**
     * 文件路径（OSS URL）
     */
    private String filePath;

    /**
     * 文件格式（pdf / docx / doc）
     */
    private String fileFormat;

}

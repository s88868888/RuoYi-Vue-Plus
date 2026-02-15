package org.dromara.resource.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.resource.domain.BizPersonnelCertificate;

import java.io.Serial;
import java.util.Date;

/**
 * 人员资格证书业务对象
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BizPersonnelCertificate.class, reverseConvertGenerate = false)
public class BizPersonnelCertificateBo extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "人员ID")
    private Long personnelId;

    @Schema(description = "证书名称")
    private String certificateName;

    @Schema(description = "证书编号")
    private String certificateNumber;

    @Schema(description = "发证日期")
    private Date issueDate;

    @Schema(description = "有效期至")
    private Date expiryDate;

    @Schema(description = "发证机关")
    private String issuingAuthority;

    @Schema(description = "证书等级")
    private String certificateLevel;

    @Schema(description = "专业")
    private String major;

    @Schema(description = "证书图片")
    private String certificateImage;

    @Schema(description = "备注")
    private String remark;

}

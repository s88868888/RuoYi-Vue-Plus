package org.dromara.resource.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.dromara.resource.domain.BizQualification;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 企业资质视图对象 biz_qualification
 *
 * @author ruoyi
 * @date 2026-02-11
 */
@Data
@AutoMapper(target = BizQualification.class)
public class BizQualificationVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "关联部门ID")
    private Long deptId;

    @Schema(description = "证书编号")
    private String certNumber;

    @Schema(description = "证书名称")
    private String certName;

    @Schema(description = "证书类别")
    private String certCategory;

    @Schema(description = "证书状态")
    private String certStatus;

    @Schema(description = "发证机关")
    private String issuingAuthority;

    @Schema(description = "有效期开始时间")
    private Date validStartDate;

    @Schema(description = "有效期结束时间")
    private Date validEndDate;

    @Schema(description = "证书图片")
    private String certImages;

    @Schema(description = "数据权限类型（0公域 1私域）")
    private String dataPermissionType;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "创建人")
    private String createByName;

}
